/**
 *
 */
package one.tracking.framework.dto;

import java.util.List;
import lombok.Builder;
import lombok.Data;
import one.tracking.framework.entity.ParticipantImportStatus;

/**
 * @author Marko Voß
 *
 */
@Data
@Builder
public class ParticipantImportFeedbackDto {

  private List<ParticipantImportEntryDto> entries;

  private int countSkipped;
  private int countFailed;
  private int countSuccess;

  private ParticipantImportStatus status;
}
