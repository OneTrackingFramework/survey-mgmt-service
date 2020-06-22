/**
 *
 */
package one.tracking.framework.service;

import static one.tracking.framework.entity.DataConstants.TOKEN_CONFIRM_LENGTH;
import static one.tracking.framework.entity.DataConstants.TOKEN_VERIFY_LENGTH;
import java.io.BufferedInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.regex.Pattern;
import javax.annotation.PostConstruct;
import org.apache.poi.EncryptedDocumentException;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Row.MissingCellPolicy;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.util.UriComponentsBuilder;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;
import one.tracking.framework.component.FileUploadManagementComponent;
import one.tracking.framework.config.TimeoutProperties;
import one.tracking.framework.dto.ParticipantInvitationDto;
import one.tracking.framework.dto.TableUploadFeedbackDto;
import one.tracking.framework.dto.VerificationDto;
import one.tracking.framework.entity.DeviceToken;
import one.tracking.framework.entity.User;
import one.tracking.framework.entity.Verification;
import one.tracking.framework.repo.DeviceTokenRepository;
import one.tracking.framework.repo.UserRepository;
import one.tracking.framework.repo.VerificationRepository;
import one.tracking.framework.support.JWTHelper;
import one.tracking.framework.support.ServiceUtility;

/**
 * @author Marko Voß
 *
 */
@Service
public class AuthService {

  private static final Logger LOG = LoggerFactory.getLogger(AuthService.class);

  private static final Pattern PATTERN_EMAIL_SIMPLE = Pattern.compile("^.+[@].+[.].+$");

  @Autowired
  private UserRepository userRepository;

  @Autowired
  private VerificationRepository verificationRepository;

  @Autowired
  private DeviceTokenRepository deviceTokenRepository;

  @Autowired
  private SendGridService emailService;

  @Autowired
  private ServiceUtility utility;

  @Autowired
  private TemplateEngine templateEngine;

  @Autowired
  private JWTHelper jwtHelper;

  @Autowired
  private FileUploadManagementComponent fileManagementComponent;

  @Autowired
  private TimeoutProperties timeoutProperties;

  @Value("${app.public.url}")
  private String publicUrl;

  @Value("${app.custom.uri.prefix}")
  private String customUriPrefix;

  private UriComponentsBuilder publicUrlBuilder;

  @PostConstruct
  public void init() {

    // throws IllegalArgumentException on invalid URIs -> startup will fail if URL is invalid
    this.publicUrlBuilder = UriComponentsBuilder.fromUriString(this.publicUrl);
  }

  public String verifyEmail(final VerificationDto verificationDto) throws IOException {

    final Optional<Verification> verificationOp =
        this.verificationRepository.findByHashAndVerified(verificationDto.getVerificationToken(), false);

    if (verificationOp.isEmpty())
      throw new IllegalArgumentException();

    final Verification verification = verificationOp.get();

    // Validate if verification is still valid

    final Instant instant =
        verification.getUpdatedAt() == null ? verification.getCreatedAt() : verification.getUpdatedAt();
    if (instant.plusSeconds(this.timeoutProperties.getVerification().toSeconds()).isBefore(Instant.now())) {

      LOG.info("Expired email verification requested.");
      throw new IllegalArgumentException(); // keep silent about it
    }

    // Update verification - do not delete hash to avoid other users receiving the same hash later
    verification.setVerified(true);
    verification.setUpdatedAt(Instant.now());
    this.verificationRepository.save(verification);

    final Optional<User> userOp = this.userRepository.findByUserToken(verificationDto.getConfirmationToken());
    User user = null;

    final String newUserToken = getValidConfirmationToken();

    if (userOp.isEmpty()) {
      // Generate new User ID
      user = this.userRepository.save(User.builder().userToken(newUserToken).build());
    } else {
      final User existingUser = userOp.get();
      existingUser.setUserToken(newUserToken);
      user = this.userRepository.save(existingUser);
    }

    sendConfirmationEmail(verification.getEmail(), user.getUserToken());

    return this.jwtHelper.createJWT(user.getId(), this.timeoutProperties.getAccess().toSeconds());
  }

  public boolean registerParticipant(final ParticipantInvitationDto registration, final boolean autoUpdateInvitation)
      throws IOException {

    final String verificationToken = getValidVerificationToken();

    final Optional<Verification> verificationOp = this.verificationRepository.findByEmail(registration.getEmail());

    boolean continueInivitation = false;

    if (verificationOp.isEmpty()) {
      // add new entity
      final Verification verification = Verification.builder()
          .email(registration.getEmail())
          .hash(verificationToken)
          .verified(false)
          .build();
      this.verificationRepository.save(verification);
      continueInivitation = true;

    } else if (autoUpdateInvitation) {
      // update entity
      final Verification verification = verificationOp.get();
      verification.setUpdatedAt(Instant.now());
      verification.setVerified(false);
      verification.setHash(verificationToken);
      this.verificationRepository.save(verification);
      continueInivitation = true;
    }

    if (continueInivitation)
      return sendRegistrationEmail(registration.getEmail(), verificationToken, registration.getConfirmationToken());

    return false;
  }

  public TableUploadFeedbackDto uploadParticipantsFile(final String userId, final MultipartFile file)
      throws IOException {

    final Path tempFile = Files.createTempFile("import", ".tmp");
    final List<String> headers = new ArrayList<>();

    final long bytesWritten = Files.copy(file.getInputStream(), tempFile, StandardCopyOption.REPLACE_EXISTING);
    final long timeout = this.fileManagementComponent.addTempFile(userId, tempFile);

    if (bytesWritten > 0) {

      final DataFormatter formatter = new DataFormatter();

      try (Workbook workbook = WorkbookFactory.create(new BufferedInputStream(
          Files.newInputStream(tempFile, StandardOpenOption.READ)))) {

        final Sheet sheet = workbook.getSheetAt(0);
        final Row row = sheet.getRow(0);

        for (final Cell cell : row) {
          headers.add(formatter.formatCellValue(cell));
        }

        if (headers.isEmpty())
          throw new IllegalArgumentException("Uploaded file contains no header elements in the first row.");

        return TableUploadFeedbackDto.builder()
            .headers(headers)
            .timeout(timeout)
            .build();
      }
    } else
      throw new IllegalArgumentException("Empty file uploaded.");

  }

  public void performParticipantsImport(final String userId, final int selectedHeaderIndex) throws Exception {

    this.fileManagementComponent.performAction(userId, file -> {

      return performParticipantsImportInternal(file, selectedHeaderIndex);
    });
  }

  private Void performParticipantsImportInternal(final Path file, final int selectedHeaderIndex)
      throws EncryptedDocumentException, IOException {

    LOG.debug("IMPORT: Performing participant import");

    final DataFormatter formatter = new DataFormatter();

    try (Workbook workbook = WorkbookFactory.create(new BufferedInputStream(
        Files.newInputStream(file, StandardOpenOption.READ)))) {

      final Sheet sheet = workbook.getSheetAt(0);
      for (final Row row : sheet) {

        if (row.getRowNum() == 0) // Skip header
          continue;

        final Cell cell = row.getCell(selectedHeaderIndex, MissingCellPolicy.RETURN_BLANK_AS_NULL);

        String email = formatter.formatCellValue(cell);

        LOG.debug("IMPORT: Current email entry: {}", email);

        if (email == null)
          continue;

        email = email.trim();

        if (email.isEmpty() || !PATTERN_EMAIL_SIMPLE.matcher(email).matches())
          continue;

        try {
          if (!registerParticipant(ParticipantInvitationDto.builder().email(email).build(), false)) {
            LOG.info("Participant invitation '{}' has been skipped as an invitation does already exist.", email);
          }
        } catch (final IOException e) {
          if (LOG.isDebugEnabled())
            LOG.debug("Participant invitation failed.", e);
          else
            LOG.warn("Participant invitation failed: {}", e.getMessage());
        }
      }
    }

    return null;
  }

  public String handleVerificationRequest(final String verificationToken, final String userToken) {

    final String path =
        userToken == null || userToken.isBlank() ? verificationToken : verificationToken + "/" + userToken;

    final Context context = new Context();
    context.setVariable("customURI", this.customUriPrefix + "://verify/" + path);

    return this.templateEngine.process("verifyTemplate", context);
  }

  private String getValidConfirmationToken() {

    final String hash = this.utility.generateString(TOKEN_CONFIRM_LENGTH);
    if (this.userRepository.existsByUserToken(hash))
      return getValidConfirmationToken(); // repeat

    return hash;
  }

  /**
   *
   * @return
   */
  private String getValidVerificationToken() {

    final String hash = this.utility.generateString(TOKEN_VERIFY_LENGTH);
    if (this.verificationRepository.existsByHash(hash))
      return getValidVerificationToken(); // repeat

    return hash;
  }

  /**
   * @param email
   * @param verificationToken
   * @return
   * @throws IOException
   */
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

    LOG.debug("Sending email to '{}' with verification link: '{}'", email, publicLink);

    final Context context = new Context();
    context.setVariable("link", publicLink);
    final String message = this.templateEngine.process("registrationTemplate", context);

    return this.emailService.sendHTML(email, "Registration", message);
  }

  /**
   *
   * @param email
   * @param hash
   * @throws IOException
   */
  private void sendConfirmationEmail(final String email, final String hash) throws IOException {

    LOG.debug("Sending email to '{}' with confirmation token: '{}'", email, hash);

    final Context context = new Context();
    context.setVariable("token", hash);
    final String message = this.templateEngine.process("confirmationTemplate", context);
    final boolean success = this.emailService.sendHTML(email, "Confirmation", message);

    if (!success)
      throw new IOException("Sending email to recipient '" + email + "' was not successful.");
  }

  /**
   * @param name
   * @param deviceToken
   */
  @Transactional
  public void registerDeviceToken(final String userId, final String deviceToken) {

    final User user = this.userRepository.findById(userId).get();

    final Optional<DeviceToken> deviceTokenOp = this.deviceTokenRepository.findByUserAndToken(user, deviceToken);

    if (deviceTokenOp.isPresent())
      return;

    final List<DeviceToken> otherUserTokens = this.deviceTokenRepository.findByUserNotAndToken(user, deviceToken);

    if (!otherUserTokens.isEmpty())
      this.deviceTokenRepository.deleteInBatch(otherUserTokens);

    this.deviceTokenRepository.save(DeviceToken.builder()
        .user(user)
        .token(deviceToken)
        .build());
  }
}
