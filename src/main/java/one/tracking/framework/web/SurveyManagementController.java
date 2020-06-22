/**
 *
 */
package one.tracking.framework.web;

import java.io.IOException;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import javax.servlet.http.HttpServletResponse;
import javax.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;
import org.springframework.util.Assert;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import one.tracking.framework.dto.ParticipantInvitationDto;
import one.tracking.framework.dto.TableUploadFeedbackDto;
import one.tracking.framework.service.AuthService;
import one.tracking.framework.service.SurveyManagementService;
import springfox.documentation.annotations.ApiIgnore;

/**
 * @author Marko Voß
 *
 */
@RestController
@RequestMapping("/manage")
public class SurveyManagementController {

  @Autowired
  private AuthService authService;

  @Autowired
  private SurveyManagementService surveyManagementService;

  @RequestMapping(
      method = RequestMethod.GET,
      path = "/test")
  public Authentication testAD(
      @ApiIgnore
      final Authentication authentication) {

    return authentication;
  }
  /*
   * Participants
   */

  @RequestMapping(
      method = RequestMethod.POST,
      path = "/participant/invite")
  public void registerParticipant(
      @RequestBody
      @Valid
      final ParticipantInvitationDto registration,
      @ApiIgnore
      final Authentication authentication) throws IOException {

    this.authService.registerParticipant(registration, true);
  }

  @RequestMapping(
      method = RequestMethod.POST,
      path = "/participant/import/upload",
      consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
  public TableUploadFeedbackDto uploadParticipantsFile(
      @RequestParam("file")
      final MultipartFile file,
      @ApiIgnore
      final Authentication authentication) throws IOException {

    return this.authService.uploadParticipantsFile(authentication.getName(), file);
  }

  @RequestMapping(
      method = RequestMethod.POST,
      path = "/participant/import/perform")
  public void importParticipants(
      @RequestParam("headerIndex")
      final int selectedHeaderIndex,
      @ApiIgnore
      final Authentication authentication) throws Exception {

    this.authService.performParticipantsImport(null, selectedHeaderIndex);
  }

  /*
   * Export
   */

  @RequestMapping(
      method = RequestMethod.GET,
      path = "/export")
  public void export(
      @RequestParam("from")
      @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
      final LocalDateTime startTime,
      @RequestParam("to")
      @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
      final LocalDateTime endTime,
      @ApiIgnore
      final HttpServletResponse response) throws IOException {

    Assert.isTrue(startTime.isBefore(endTime), "'from' datetime value must be before 'to' datetime value.");

    final DateTimeFormatter formatter = DateTimeFormatter.ofPattern("YYYYMMdd_HHmmss")
        // .withLocale(Locale.UK)
        .withZone(ZoneOffset.UTC);

    final String filename = "export_" + formatter.format(Instant.now()) + ".xlsx";

    response.setContentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
    response.setHeader(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"");

    this.surveyManagementService.exportData(
        startTime.toInstant(ZoneOffset.UTC),
        endTime.toInstant(ZoneOffset.UTC),
        response.getOutputStream());
  }

  /*
   * Surveys
   */

  @RequestMapping(
      method = RequestMethod.GET,
      path = "/survey")
  public void getSurveys(/* TODO */) {
    throw new UnsupportedOperationException();
  }

  @RequestMapping(
      method = RequestMethod.GET,
      path = "/survey/{surveyId}")
  public void getSurvey(/* TODO */
      @PathVariable("surveyId")
      final Long surveyId) {
    throw new UnsupportedOperationException();
  }

  @RequestMapping(
      method = RequestMethod.POST,
      path = "/survey")
  public void createSurvey(/* TODO */) {
    throw new UnsupportedOperationException();
  }

  @RequestMapping(
      method = RequestMethod.POST,
      path = "/survey/{surveyId}")
  public void updateSurvey(/* TODO */
      @PathVariable("surveyId")
      final Long surveyId) {
    throw new UnsupportedOperationException();
  }

  @RequestMapping(
      method = RequestMethod.DELETE,
      path = "/survey/{surveyId}")
  public void deleteSurvey(/* TODO */
      @PathVariable("surveyId")
      final Long surveyId) {
    throw new UnsupportedOperationException();
  }

  /*
   * Questions
   */

  @RequestMapping(
      method = RequestMethod.GET,
      path = "/survey/{surveyId}/question")
  public void getQuestions(
      @PathVariable("surveyId")
      final Long surveyId/* TODO */) {
    throw new UnsupportedOperationException();
  }

  @RequestMapping(
      method = RequestMethod.GET,
      path = "/survey/{surveyId}/question/{questionId}")
  public void getQuestion(/* TODO */
      @PathVariable("surveyId")
      final Long surveyId,
      @PathVariable("questionId")
      final Long questionId) {
    throw new UnsupportedOperationException();
  }

  @RequestMapping(
      method = RequestMethod.POST,
      path = "/survey/{surveyId}/question")
  public void createQuestion(/* TODO */
      @PathVariable("surveyId")
      final Long surveyId) {
    throw new UnsupportedOperationException();
  }

  @RequestMapping(
      method = RequestMethod.POST,
      path = "/survey/{surveyId}/question/{questionId}")
  public void updateQuestion(/* TODO */
      @PathVariable("surveyId")
      final Long surveyId,
      @PathVariable("questionId")
      final Long questionId) {
    throw new UnsupportedOperationException();
  }

  @RequestMapping(
      method = RequestMethod.DELETE,
      path = "/survey/{surveyId}/question/{questionId}")
  public void deleteQuestion(/* TODO */
      @PathVariable("surveyId")
      final Long surveyId,
      @PathVariable("questionId")
      final Long questionId) {
    throw new UnsupportedOperationException();
  }

  /*
   * Choice Answers (Bad Request if question is not of type CHOICE)
   */

  @RequestMapping(
      method = RequestMethod.GET,
      path = "/survey/{surveyId}/question/{questionId}/answer")
  public void getAnswers(/* TODO */) {
    throw new UnsupportedOperationException();
  }

  @RequestMapping(
      method = RequestMethod.GET,
      path = "/survey/{surveyId}/question/{questionId}/answer/{answerId}")
  public void getAnswer(/* TODO */
      @PathVariable("surveyId")
      final Long surveyId,
      @PathVariable("questionId")
      final Long questionId,
      @PathVariable("answerId")
      final Long answerId) {

    throw new UnsupportedOperationException();
  }

  @RequestMapping(
      method = RequestMethod.POST,
      path = "/survey/{surveyId}/question/{questionId}/answer")
  public void createAnswer(/* TODO */
      @PathVariable("surveyId")
      final Long surveyId,
      @PathVariable("questionId")
      final Long questionId) {

    throw new UnsupportedOperationException();
  }

  @RequestMapping(
      method = RequestMethod.POST,
      path = "/survey/{surveyId}/question/{questionId}/answer/{answerId}")
  public void updateAnswer(/* TODO */
      @PathVariable("surveyId")
      final Long surveyId,
      @PathVariable("questionId")
      final Long questionId,
      @PathVariable("answerId")
      final Long answerId) {

    throw new UnsupportedOperationException();
  }

  @RequestMapping(
      method = RequestMethod.DELETE,
      path = "/survey/{surveyId}/question/{questionId}/answer/{answerId}")
  public void deleteAnswer(/* TODO */
      @PathVariable("surveyId")
      final Long surveyId,
      @PathVariable("questionId")
      final Long questionId,
      @PathVariable("answerId")
      final Long answerId) {

    throw new UnsupportedOperationException();
  }
}
