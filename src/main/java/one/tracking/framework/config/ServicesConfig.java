/**
 *
 */
package one.tracking.framework.config;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * @author Marko Voß
 *
 */
@Configuration
@EnableAsync
public class ServicesConfig {

  @Autowired
  private PlatformTransactionManager transactionManager;

  @Bean
  public TransactionTemplate transactionTemplate() {
    final TransactionTemplate tt = new TransactionTemplate(this.transactionManager);
    tt.setIsolationLevel(TransactionDefinition.ISOLATION_READ_UNCOMMITTED);
    tt.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    return tt;
  }
}
