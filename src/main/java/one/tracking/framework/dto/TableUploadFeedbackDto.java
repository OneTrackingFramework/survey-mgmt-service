/**
 *
 */
package one.tracking.framework.dto;

import java.util.List;
import lombok.Builder;
import lombok.Data;

/**
 * @author Marko Voß
 *
 */
@Data
@Builder
public class TableUploadFeedbackDto {

  private List<String> headers;
  private Long timeout;
}
