/**
 *
 */
package one.tracking.framework.component;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.stereotype.Component;

/**
 * @author Marko Voß
 *
 */
@Component
public class AuthenticationSupport {

  public String getUserIdentifier() {

    final Authentication authentication = SecurityContextHolder.getContext().getAuthentication();

    if (authentication == null)
      throw new IllegalStateException(
          "No authentication available. Did you call this method outside of request or session scope?");

    if (authentication.getPrincipal() instanceof OidcUser) {
      final OidcUser user = (OidcUser) authentication.getPrincipal();
      return (user.getEmail() == null) ? user.getSubject() : user.getEmail();
    }

    return authentication.getName();
  }
}
