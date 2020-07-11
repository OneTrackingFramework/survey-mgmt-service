/**
 *
 */
package one.tracking.framework.service;

import static one.tracking.framework.entity.DataConstants.TOKEN_VERIFY_LENGTH;
import java.io.BufferedInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import javax.annotation.PostConstruct;
import javax.persistence.EntityManager;
import javax.persistence.NoResultException;
import javax.persistence.TypedQuery;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Row.MissingCellPolicy;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.util.Assert;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.util.UriComponentsBuilder;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;
import lombok.extern.slf4j.Slf4j;
import one.tracking.framework.component.AsyncExecutor;
import one.tracking.framework.component.AsyncExecutor.AsyncTask;
import one.tracking.framework.component.AuthenticationSupport;
import one.tracking.framework.domain.InvitationFeedback;
import one.tracking.framework.dto.ParticipantImportEntryDto;
import one.tracking.framework.dto.ParticipantImportFeedbackDto;
import one.tracking.framework.entity.ParticipantImport;
import one.tracking.framework.entity.ParticipantImportStatus;
import one.tracking.framework.entity.Verification;
import one.tracking.framework.entity.VerificationState;
import one.tracking.framework.support.ServiceUtility;

/**
 * @author Marko Voß
 *
 */
@Slf4j
@Service
public class ParticipantService {

  private static final Pattern PATTERN_EMAIL_SIMPLE = Pattern.compile("^\\S+[@]\\S+[.]\\S+$");

  @Autowired
  private TransactionTemplate transactionTemplate;

  @Autowired
  private EntityManager entityManager;

  @Autowired
  private SendGridService emailService;

  @Autowired
  private ServiceUtility utility;

  @Autowired
  private TemplateEngine templateEngine;

  @Autowired
  private AsyncExecutor asyncExecutor;

  @Autowired
  private AuthenticationSupport authenticationSupport;

  @Value("${app.public.url}")
  private String publicUrl;

  private UriComponentsBuilder publicUrlBuilder;

  @PostConstruct
  public void init() {

    // throws IllegalArgumentException on invalid URIs -> startup will fail if URL is invalid
    this.publicUrlBuilder = UriComponentsBuilder.fromUriString(this.publicUrl);
  }

  public String importParticipants(final MultipartFile file, final int selectedHeaderIndex) throws Exception {

    final Path tempFile = Files.createTempFile("import", ".tmp");
    final long bytesWritten = Files.copy(file.getInputStream(), tempFile, StandardCopyOption.REPLACE_EXISTING);

    if (bytesWritten == 0)
      throw new IllegalArgumentException("Empty file uploaded.");

    final ParticipantImport participantImport = ParticipantImport.builder()
        .createdBy(this.authenticationSupport.getUserIdentifier())
        .status(ParticipantImportStatus.IN_PROGRESS)
        .build();

    this.transactionTemplate.executeWithoutResult(status -> {
      this.entityManager.persist(participantImport);
      status.flush();
    });

    final String importId = participantImport.getId();
    /*
     * Pass the current authentication from the current request scope as the asynchronous execution
     * cannot get any authentication from the application context.
     */
    final String userId = this.authenticationSupport.getUserIdentifier();

    this.asyncExecutor.execute(new AsyncTask<Void>() {

      @Override
      public Void call() throws Exception {
        performParticipantsImportInternal(userId, importId, tempFile, selectedHeaderIndex);
        return null;
      }

      @Override
      public void after() throws Exception {
        try {
          Files.deleteIfExists(tempFile);
        } catch (final IOException e) {
          log.warn("Unable to delete temp file.", e);
        }
      }
    });

    return importId;
  }

  public void cancelImport(final String importId) {

    final TypedQuery<ParticipantImport> query =
        this.entityManager.createNamedQuery("ParticipantImport.findByIdAndStatus", ParticipantImport.class);
    query.setParameter(1, importId);
    query.setParameter(2, ParticipantImportStatus.IN_PROGRESS);

    this.transactionTemplate.executeWithoutResult(status -> {
      try {

        final ParticipantImport participantImport = query.getSingleResult();
        participantImport.setStatus(ParticipantImportStatus.CANCELLED);
        this.entityManager.merge(participantImport);

      } catch (final NoResultException e) {
        // ignore
      }
    });
  }

  public ParticipantImportFeedbackDto getImportedParticipants(final String importId, final Integer startIndex,
      final Integer limit) {

    Assert.notNull(importId, "ImportId must not be null.");

    final ParticipantImport participantImport = getParticipantImport(importId);

    if (participantImport == null)
      throw new IllegalArgumentException("No import found for id: " + importId);

    final TypedQuery<Verification> query =
        this.entityManager.createNamedQuery("Verification.findByImportIdOrderByCreatedAtAsc", Verification.class);
    query.setParameter(1, importId);

    if (startIndex != null)
      query.setFirstResult(startIndex);
    if (limit != null)
      query.setMaxResults(limit);

    final List<Verification> verifications = this.transactionTemplate.execute(status -> {
      return query.getResultList();
    });

    return ParticipantImportFeedbackDto.builder()
        .countFailed(participantImport.getCountFailed())
        .countSkipped(participantImport.getCountSkipped())
        .countSuccess(participantImport.getCountSuccess())
        .status(participantImport.getStatus())
        .entries(verifications.stream().map(verification -> ParticipantImportEntryDto.builder()
            .email(verification.getEmail())
            .state(verification.getState())
            .build())
            .collect(Collectors.toList()))
        .build();

  }

  private void performParticipantsImportInternal(
      final String userId,
      final String importId,
      final Path file,
      final int headerIndex)
      throws Exception {

    log.debug("IMPORT: Performing participant import");

    try (Workbook workbook = WorkbookFactory.create(new BufferedInputStream(
        Files.newInputStream(file, StandardOpenOption.READ)))) {

      handleSheet(workbook.getSheetAt(0), userId, importId, headerIndex);

    } catch (final Exception e) {
      log.error("Unable to perform import.", e);
      throw e;

    } finally {

      final TypedQuery<ParticipantImport> query =
          this.entityManager.createNamedQuery("ParticipantImport.findByIdAndStatus", ParticipantImport.class);
      query.setParameter(1, importId);
      query.setParameter(2, ParticipantImportStatus.IN_PROGRESS);

      this.transactionTemplate.executeWithoutResult(status -> {
        try {

          final ParticipantImport participantImport = query.getSingleResult();
          participantImport.setStatus(ParticipantImportStatus.DONE);
          this.entityManager.merge(participantImport);
          status.flush();

        } catch (final NoResultException e) {
          // ignore
        }
      });
    }
  }

  private void handleSheet(final Sheet sheet, final String userId, final String importId, final int headerIndex) {

    final DataFormatter formatter = new DataFormatter();
    int countSkipped = 0;

    for (final Row row : sheet) {

      if (row.getRowNum() == 0) // Skip header
        continue;

      final Cell cell = row.getCell(headerIndex, MissingCellPolicy.RETURN_BLANK_AS_NULL);

      String email = formatter.formatCellValue(cell);

      log.debug("IMPORT: Current email entry: {}", email);

      if (email == null) {
        countSkipped++;
        continue;
      }

      email = email.trim();

      if (email.isEmpty() || !PATTERN_EMAIL_SIMPLE.matcher(email).matches()) {
        countSkipped++;
        continue;
      }

      final ParticipantImport participantImport = getParticipantImport(importId, ParticipantImportStatus.IN_PROGRESS);
      if (participantImport == null)
        break; // cancel task

      final InvitationFeedback feedback = registerParticipant(userId, email, null, participantImport, false);

      final int finalCountSkipped = countSkipped;

      this.transactionTemplate.executeWithoutResult(status -> {

        final TypedQuery<ParticipantImport> query =
            this.entityManager.createNamedQuery("ParticipantImport.findById", ParticipantImport.class);
        query.setParameter(1, importId);

        final ParticipantImport entity = query.getSingleResult();

        switch (feedback) {
          case FAILED:
            entity.setCountSkipped(participantImport.getCountSkipped() + finalCountSkipped);
            entity.setCountFailed(participantImport.getCountFailed() + 1);
            break;
          case SKIPPED:
            entity.setCountSkipped(participantImport.getCountSkipped() + finalCountSkipped + 1);
            break;
          case SUCCESS: {
            entity.setCountSkipped(participantImport.getCountSkipped() + finalCountSkipped);
            entity.setCountSuccess(participantImport.getCountSuccess() + 1);
            break;
          }
        }

        this.entityManager.merge(entity);
        status.flush();
      });

      countSkipped = 0;
    }
  }

  private ParticipantImport getParticipantImport(final String id) {

    final TypedQuery<ParticipantImport> query =
        this.entityManager.createNamedQuery("ParticipantImport.findById", ParticipantImport.class);
    query.setParameter(1, id);

    return this.transactionTemplate.execute(status -> {
      try {
        return query.getSingleResult();
      } catch (final NoResultException e) {
        return null;
      }
    });
  }

  private ParticipantImport getParticipantImport(final String id, final ParticipantImportStatus importStatus) {

    final TypedQuery<ParticipantImport> query =
        this.entityManager.createNamedQuery("ParticipantImport.findByIdAndStatus", ParticipantImport.class);
    query.setParameter(1, id);
    query.setParameter(2, importStatus);

    return this.transactionTemplate.execute(status -> {
      try {
        return query.getSingleResult();
      } catch (final NoResultException e) {
        return null;
      }
    });
  }

  public InvitationFeedback registerParticipant(final String email,
      final String confirmationToken,
      final boolean autoUpdateInvitation) throws IOException {

    return registerParticipant(
        this.authenticationSupport.getUserIdentifier(),
        email,
        confirmationToken,
        null,
        autoUpdateInvitation);
  }

  private InvitationFeedback registerParticipant(
      final String userId,
      final String email,
      final String confirmationToken,
      final ParticipantImport participantImport,
      final boolean autoUpdateInvitation) {

    final String verificationToken = getValidVerificationToken();

    final TypedQuery<Verification> query =
        this.entityManager.createNamedQuery("Verification.findByEmail", Verification.class);
    query.setParameter(1, email);

    final Optional<Verification> verificationOp = this.transactionTemplate.execute(status -> {
      try {
        return Optional.of(query.getSingleResult());
      } catch (final NoResultException e) {
        return Optional.empty();
      }
    });

    boolean emailSentSuccessfully = false;

    if (!(verificationOp.isEmpty() || autoUpdateInvitation))
      return InvitationFeedback.SKIPPED;


    try {
      emailSentSuccessfully = sendRegistrationEmail(email, verificationToken, confirmationToken);
    } catch (final IOException e) {
      log.warn("Unable to send email.", e);
    }

    if (verificationOp.isEmpty()) {
      // add new entity
      final Verification verification = Verification.builder()
          .email(email)
          .hash(verificationToken)
          .state(emailSentSuccessfully ? VerificationState.PENDING : VerificationState.ERROR)
          .createdBy(userId)
          .participantImport(participantImport)
          .build();

      this.transactionTemplate.executeWithoutResult(status -> {
        this.entityManager.persist(verification);
        status.flush();
      });
    } else {
      // update entity
      final Verification verification = verificationOp.get();
      verification.setUpdatedAt(Instant.now());
      verification.setUpdatedBy(userId);
      verification.setParticipantImport(participantImport);
      verification.setState(emailSentSuccessfully ? VerificationState.PENDING : VerificationState.ERROR);
      verification.setHash(verificationToken);

      this.transactionTemplate.executeWithoutResult(status -> {
        this.entityManager.persist(verification);
        status.flush();
      });

    }

    return emailSentSuccessfully ? InvitationFeedback.SUCCESS : InvitationFeedback.FAILED;
  }

  private String getValidVerificationToken() {

    return this.utility.generateValidToken(TOKEN_VERIFY_LENGTH, 10, token -> {

      final TypedQuery<Boolean> query = this.entityManager.createNamedQuery("Verification.existsByHash", Boolean.class);
      query.setParameter(1, token);

      return this.transactionTemplate.execute(status -> {
        try {
          return query.getSingleResult();
        } catch (final NoResultException e) {
          return false;
        }
      });
    });
  }

  private boolean sendRegistrationEmail(final String email, final String verificationToken, final String userToken)
      throws IOException {

    final UriComponentsBuilder builder = this.publicUrlBuilder.cloneBuilder()
        .path("/auth/verify")
        .queryParam("token", verificationToken);

    if (userToken != null && !userToken.isBlank())
      builder.queryParam("userToken", userToken);

    final String publicLink = builder
        .build()
        .encode()
        .toString();

    log.debug("Sending email to '{}' with verification link: '{}'", email, publicLink);

    final Context context = new Context();
    context.setVariable("link", publicLink);
    final String message = this.templateEngine.process("registrationTemplate", context);

    return this.emailService.sendHTML(email, "Registration", message);
  }
}
