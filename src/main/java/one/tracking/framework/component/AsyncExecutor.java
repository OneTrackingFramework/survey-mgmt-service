/**
 *
 */
package one.tracking.framework.component;

import java.util.concurrent.Callable;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.util.Assert;

/**
 * @author Marko Voß
 *
 */
@Component
public class AsyncExecutor {

  public interface AsyncTask<V> extends Callable<V> {

    default void before() throws Exception {}

    default void after() throws Exception {}
  }

  @Async
  public <V> V execute(final AsyncTask<V> task) throws Exception {

    Assert.notNull(task, "Task must not be null.");

    task.before();

    try {
      return task.call();
    } catch (final Exception e) {
      throw e;
    } finally {
      task.after();
    }
  }
}
