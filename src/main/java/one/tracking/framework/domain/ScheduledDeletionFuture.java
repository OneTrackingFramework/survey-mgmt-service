/**
 *
 */
package one.tracking.framework.domain;

import java.nio.file.Path;
import java.util.concurrent.Delayed;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;

/**
 * @author Marko Voß
 *
 */
@Data 
@Builder
@AllArgsConstructor
public class ScheduledDeletionFuture<V> implements ScheduledFuture<V> {

  private ScheduledFuture<V> origin;

  private Path file;

  /**
   * @param unit
   * @return
   * @see java.util.concurrent.Delayed#getDelay(java.util.concurrent.TimeUnit)
   */
  @Override
  public long getDelay(final TimeUnit unit) {
    return this.origin.getDelay(unit);
  }

  /**
   * @param mayInterruptIfRunning
   * @return
   * @see java.util.concurrent.Future#cancel(boolean)
   */
  @Override
  public boolean cancel(final boolean mayInterruptIfRunning) {
    return this.origin.cancel(mayInterruptIfRunning);
  }

  /**
   * @param o
   * @return
   * @see java.lang.Comparable#compareTo(java.lang.Object)
   */
  @Override
  public int compareTo(final Delayed o) {
    return this.origin.compareTo(o);
  }

  /**
   * @return
   * @see java.util.concurrent.Future#isCancelled()
   */
  @Override
  public boolean isCancelled() {
    return this.origin.isCancelled();
  }

  /**
   * @return
   * @see java.util.concurrent.Future#isDone()
   */
  @Override
  public boolean isDone() {
    return this.origin.isDone();
  }

  /**
   * @return
   * @throws InterruptedException
   * @throws ExecutionException
   * @see java.util.concurrent.Future#get()
   */
  @Override
  public V get() throws InterruptedException, ExecutionException {
    return this.origin.get();
  }

  /**
   * @param timeout
   * @param unit
   * @return
   * @throws InterruptedException
   * @throws ExecutionException
   * @throws TimeoutException
   * @see java.util.concurrent.Future#get(long, java.util.concurrent.TimeUnit)
   */
  @Override
  public V get(final long timeout, final TimeUnit unit)
      throws InterruptedException, ExecutionException, TimeoutException {
    return this.origin.get(timeout, unit);
  }


}
