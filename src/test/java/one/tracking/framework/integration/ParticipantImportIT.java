/**
 *
 */
package one.tracking.framework.integration;

import static org.awaitility.Awaitility.await;
import static org.hamcrest.Matchers.blankOrNullString;
import static org.hamcrest.Matchers.greaterThan;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.lessThan;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.nullValue;
import static org.hamcrest.Matchers.oneOf;
import static org.junit.Assert.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import java.io.IOException;
import java.time.Duration;
import java.time.temporal.ChronoUnit;
import java.util.concurrent.Callable;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.AdditionalMatchers;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.annotation.DirtiesContext.ClassMode;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.junit4.SpringRunner;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import one.tracking.framework.SurveyManagementApplication;
import one.tracking.framework.dto.ParticipantImportEntryDto;
import one.tracking.framework.dto.ParticipantImportFeedbackDto;
import one.tracking.framework.dto.TokenResponseDto;
import one.tracking.framework.entity.ParticipantImportStatus;
import one.tracking.framework.entity.VerificationState;
import one.tracking.framework.service.SendGridService;

/**
 * @author Marko Voß
 *
 */
@AutoConfigureMockMvc
@TestPropertySource(locations = "classpath:application-it.properties")
@Import(ITConfiguration.class)
@RunWith(SpringRunner.class)
@SpringBootTest(classes = SurveyManagementApplication.class)
@DirtiesContext(classMode = ClassMode.AFTER_EACH_TEST_METHOD)
@ActiveProfiles("dev")
@Slf4j
public class ParticipantImportIT {

  private static final String INVALID_EMAIL = "test.1000@example.com";
  private static final String CANCEL_AT_EMAIL = "test.2000@example.com";

  private static final String ENDPOINT_MANAGE = "/manage";
  private static final String ENDPOINT_MANAGE_PARTICIPANT = ENDPOINT_MANAGE + "/participant";
  private static final String ENDPOINT_MANAGE_PARTICIPANT_IMPORT = ENDPOINT_MANAGE_PARTICIPANT + "/import";

  @MockBean
  private SendGridService sendGridService;

  @Autowired
  private MockMvc mockMvc;

  @Autowired
  private ResourceLoader resourceLoader;

  @Autowired
  private ObjectMapper mapper;

  @Before
  public void before() throws IOException {
    when(this.sendGridService.sendHTML(eq(INVALID_EMAIL), anyString(), anyString())).thenReturn(false);
    when(this.sendGridService.sendHTML(AdditionalMatchers.not(eq(INVALID_EMAIL)), anyString(), anyString()))
        .thenReturn(true);

    when(this.sendGridService.sendText(eq(INVALID_EMAIL), anyString(), anyString())).thenReturn(false);
    when(this.sendGridService.sendText(AdditionalMatchers.not(eq(INVALID_EMAIL)), anyString(), anyString()))
        .thenReturn(true);
  }

  @Test
  public void testImportSuccess() throws Exception {

    final Resource resource = this.resourceLoader.getResource("classpath:import-test.xlsx");

    assertThat(resource.exists(), is(true));

    final MockMultipartFile file =
        new MockMultipartFile("file", resource.getFilename(), null, resource.getInputStream());

    this.mockMvc.perform(MockMvcRequestBuilders.multipart(ENDPOINT_MANAGE_PARTICIPANT_IMPORT)
        .file(file)
        .param("headerIndex", "2")
        .with(csrf()))
        .andExpect(status().isUnauthorized());

    final MvcResult result = this.mockMvc.perform(MockMvcRequestBuilders.multipart(ENDPOINT_MANAGE_PARTICIPANT_IMPORT)
        .file(file)
        .param("headerIndex", "2")
        .with(csrf())
        .with(httpBasic("admin", "admin")))
        .andExpect(status().isOk())
        .andReturn();

    final TokenResponseDto tokenResponse =
        this.mapper.readValue(result.getResponse().getContentAsByteArray(), TokenResponseDto.class);

    assertThat(tokenResponse, is(not(nullValue())));
    assertThat(tokenResponse.getToken(), is(not(blankOrNullString())));

    await()
        .atMost(Duration.of(5, ChronoUnit.MINUTES))
        .with()
        .pollInterval(Duration.of(1, ChronoUnit.SECONDS))
        .until(new ParticipantImportSuccessPoller(tokenResponse.getToken(), 0, 500));
  }

  @Test
  public void testImportCancelled() throws Exception {

    final Resource resource = this.resourceLoader.getResource("classpath:import-test.xlsx");

    assertThat(resource.exists(), is(true));

    final MockMultipartFile file =
        new MockMultipartFile("file", resource.getFilename(), null, resource.getInputStream());

    this.mockMvc.perform(MockMvcRequestBuilders.multipart(ENDPOINT_MANAGE_PARTICIPANT_IMPORT)
        .file(file)
        .param("headerIndex", "2")
        .with(csrf()))
        .andExpect(status().isUnauthorized());

    final MvcResult result = this.mockMvc.perform(MockMvcRequestBuilders.multipart(ENDPOINT_MANAGE_PARTICIPANT_IMPORT)
        .file(file)
        .param("headerIndex", "2")
        .with(csrf())
        .with(httpBasic("admin", "admin")))
        .andExpect(status().isOk())
        .andReturn();

    final TokenResponseDto tokenResponse =
        this.mapper.readValue(result.getResponse().getContentAsByteArray(), TokenResponseDto.class);

    assertThat(tokenResponse, is(not(nullValue())));
    assertThat(tokenResponse.getToken(), is(not(blankOrNullString())));

    // Start polling
    await()
        .atMost(Duration.of(5, ChronoUnit.MINUTES))
        .with()
        .pollInterval(Duration.of(1, ChronoUnit.SECONDS))
        .until(new ParticipantImportCancelledPoller(tokenResponse.getToken(), 0, 500));
  }

  @Data
  @AllArgsConstructor
  private class ParticipantImportSuccessPoller implements Callable<Boolean> {

    private String importId;
    private Integer startIndex;
    private Integer limit;

    @Override
    public Boolean call() throws Exception {

      final MvcResult importResult =
          ParticipantImportIT.this.mockMvc
              .perform(MockMvcRequestBuilders.get(ENDPOINT_MANAGE_PARTICIPANT_IMPORT + "/" + this.importId)
                  .queryParam("startIndex", this.startIndex.toString())
                  .queryParam("limit", this.limit.toString())
                  .with(csrf())
                  .with(httpBasic("admin", "admin")))
              .andExpect(status().isOk())
              .andReturn();

      final ParticipantImportFeedbackDto feedback =
          ParticipantImportIT.this.mapper.readValue(importResult.getResponse().getContentAsByteArray(),
              ParticipantImportFeedbackDto.class);

      log.info("FEEDBACK: startIndex: {}, status: {}, success: {}, failed: {}, skipped: {}, countEmails: {}",
          this.startIndex,
          feedback.getStatus(),
          feedback.getCountSuccess(),
          feedback.getCountFailed(),
          feedback.getCountSkipped(),
          feedback.getEntries().size());

      assertThat(feedback.getStatus(), is(oneOf(ParticipantImportStatus.IN_PROGRESS, ParticipantImportStatus.DONE)));

      feedback.getEntries().forEach(entry -> {
        assertThat(entry.getEmail(), is(not(blankOrNullString())));
        assertThat(entry.getState(), is(oneOf(VerificationState.ERROR, VerificationState.PENDING)));
      });

      if (feedback.getStatus() == ParticipantImportStatus.DONE && feedback.getEntries().size() == 0) {

        assertThat(feedback.getCountSuccess(), is(9998));
        assertThat(feedback.getCountFailed(), is(1));
        assertThat(feedback.getCountSkipped(), is(1));

        return true;
      }

      this.startIndex += feedback.getEntries().size();

      return false;
    }

  }

  @Data
  @AllArgsConstructor
  private class ParticipantImportCancelledPoller implements Callable<Boolean> {

    private String importId;
    private Integer startIndex;
    private Integer limit;

    @Override
    public Boolean call() throws Exception {

      final MvcResult importResult =
          ParticipantImportIT.this.mockMvc
              .perform(MockMvcRequestBuilders.get(ENDPOINT_MANAGE_PARTICIPANT_IMPORT + "/" + this.importId)
                  .queryParam("startIndex", this.startIndex.toString())
                  .queryParam("limit", this.limit.toString())
                  .with(csrf())
                  .with(httpBasic("admin", "admin")))
              .andExpect(status().isOk())
              .andReturn();

      final ParticipantImportFeedbackDto feedback =
          ParticipantImportIT.this.mapper.readValue(importResult.getResponse().getContentAsByteArray(),
              ParticipantImportFeedbackDto.class);

      log.info("FEEDBACK: startIndex: {}, status: {}, success: {}, failed: {}, skipped: {}, countEmails: {}",
          this.startIndex,
          feedback.getStatus(),
          feedback.getCountSuccess(),
          feedback.getCountFailed(),
          feedback.getCountSkipped(),
          feedback.getEntries().size());

      assertThat(feedback.getStatus(),
          is(oneOf(ParticipantImportStatus.IN_PROGRESS, ParticipantImportStatus.CANCELLED)));

      for (final ParticipantImportEntryDto entry : feedback.getEntries()) {

        assertThat(entry.getEmail(), is(not(blankOrNullString())));
        assertThat(entry.getState(), is(oneOf(VerificationState.ERROR, VerificationState.PENDING)));

        // Hook to cancel import:
        if (CANCEL_AT_EMAIL.equals(entry.getEmail())) {

          ParticipantImportIT.this.mockMvc
              .perform(MockMvcRequestBuilders.post(ENDPOINT_MANAGE_PARTICIPANT_IMPORT + "/" + this.importId)
                  .queryParam("cancel", "true")
                  .with(csrf())
                  .with(httpBasic("admin", "admin")))
              .andExpect(status().isOk());
        }
      }

      if ((feedback.getStatus() == ParticipantImportStatus.CANCELLED
          || feedback.getStatus() == ParticipantImportStatus.DONE) && feedback.getEntries().size() == 0) {

        assertThat(feedback.getCountSuccess(), is(greaterThan(0)));
        assertThat(feedback.getCountSuccess(), is(lessThan(9998)));

        // Hard to test failed and skipped

        return true;
      }

      this.startIndex += feedback.getEntries().size();

      return false;
    }

  }
}
