/**
 *
 */
package one.tracking.framework.service;

import static one.tracking.framework.entity.DataConstants.TOKEN_CONFIRM_LENGTH;
import java.io.IOException;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;
import one.tracking.framework.config.TimeoutProperties;
import one.tracking.framework.dto.VerificationDto;
import one.tracking.framework.entity.DeviceToken;
import one.tracking.framework.entity.User;
import one.tracking.framework.entity.Verification;
import one.tracking.framework.entity.VerificationState;
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
  private TimeoutProperties timeoutProperties;

  @Value("${app.custom.uri.prefix}")
  private String customUriPrefix;

  public String verifyEmail(final VerificationDto verificationDto) throws IOException {

    final Optional<Verification> verificationOp =
        this.verificationRepository.findByHashAndState(verificationDto.getVerificationToken(),
            VerificationState.PENDING);

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
    verification.setState(VerificationState.VERIFIED);
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
