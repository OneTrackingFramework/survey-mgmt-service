package one.tracking.framework.service;


import static one.tracking.framework.dto.DtoMapper.map;

import java.io.IOException;
import java.io.OutputStream;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import one.tracking.framework.component.SurveyDataExportComponent;
import one.tracking.framework.domain.SearchResult;
import one.tracking.framework.dto.DtoMapper;
import one.tracking.framework.dto.meta.SurveyDto;
import one.tracking.framework.dto.meta.question.BooleanQuestionDto;
import one.tracking.framework.dto.meta.question.ChecklistEntryDto;
import one.tracking.framework.dto.meta.question.ChecklistQuestionDto;
import one.tracking.framework.dto.meta.question.ChoiceQuestionDto;
import one.tracking.framework.dto.meta.question.QuestionDto;
import one.tracking.framework.dto.meta.question.RangeQuestionDto;
import one.tracking.framework.dto.meta.question.TextQuestionDto;
import one.tracking.framework.entity.meta.Answer;
import one.tracking.framework.entity.meta.IntervalType;
import one.tracking.framework.entity.meta.ReleaseStatusType;
import one.tracking.framework.entity.meta.ReminderType;
import one.tracking.framework.entity.meta.Survey;
import one.tracking.framework.entity.meta.container.BooleanContainer;
import one.tracking.framework.entity.meta.container.ChoiceContainer;
import one.tracking.framework.entity.meta.container.Container;
import one.tracking.framework.entity.meta.container.ContainerType;
import one.tracking.framework.entity.meta.question.BooleanQuestion;
import one.tracking.framework.entity.meta.question.ChecklistEntry;
import one.tracking.framework.entity.meta.question.ChecklistQuestion;
import one.tracking.framework.entity.meta.question.ChoiceQuestion;
import one.tracking.framework.entity.meta.question.Question;
import one.tracking.framework.entity.meta.question.QuestionType;
import one.tracking.framework.entity.meta.question.RangeQuestion;
import one.tracking.framework.entity.meta.question.TextQuestion;
import one.tracking.framework.exception.ConflictException;
import one.tracking.framework.exceptions.ResourceNotFoundException;
import one.tracking.framework.repo.AnswerRepository;
import one.tracking.framework.repo.ContainerRepository;
import one.tracking.framework.repo.QuestionRepository;
import one.tracking.framework.repo.SurveyRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/**
 * FIXME: Current DTOs are placeholder objects and can be adjusted to desired requirements. FIXME:
 * Exception handling FIXME: Inject Questions in Survey CRUD Operations
 *
 * @author Marko Voß
 */
@Service
@Slf4j
public class SurveyManagementService {

  @Autowired
  private SurveyRepository surveyRepository;

  @Autowired
  private ContainerRepository containerRepository;

  @Autowired
  private QuestionRepository questionRepository;

  @Autowired
  private AnswerRepository answerRepository;

  @Autowired
  private SurveyDataExportComponent exportComponent;

  public void exportData(final Instant startTime, final Instant endTime,
      final OutputStream outStream)
      throws IOException {

    this.exportComponent.export(startTime, endTime, outStream);
  }

  /*
  Survey
     */

  public SurveyDto createSurvey(final SurveyDto surveyDto) {

    final Survey createdSurvey = this.surveyRepository.save(Survey.builder()
        //.dependsOn()
        //.questions(survey)
        .nameId(surveyDto.getNameId())
        .title(surveyDto.getTitle())
        .description((surveyDto.getDescription()))
        .intervalType(IntervalType.NONE)
        .reminderType(ReminderType.NONE)
        .releaseStatus(ReleaseStatusType.RELEASED)
        //.questions(surveyDto.getQuestions().stream().map(question -> map(question))
        //.collect(Collectors.toList()))
        .build());

    return map(createdSurvey);
  }

  public SurveyDto getSurveyById(final long surveyId) {
    final Optional<Survey> surveyOptional = surveyRepository.findById(surveyId);
    if (surveyOptional.isEmpty()) {
      throw new ResourceNotFoundException("Survey", "id", surveyId);
    }
    return map(surveyOptional.get());
  }

  public List<SurveyDto> getAllSurveys() {
    final List<SurveyDto> surveys = new ArrayList<>();
    surveyRepository.findAll()
        .forEach(survey -> surveys.add(DtoMapper.map(survey)));
    return surveys;
  }

  public SurveyDto updateSurvey(final long surveyId, final SurveyDto newSurveyDto) {

    // get survey from DB
    final SurveyDto surveyDto = getSurveyById(surveyId);

    // update existing survey with new survey
    return createSurvey(surveyDto.builder()
        .id(surveyId)
        .nameId(newSurveyDto.getNameId())
        .title(newSurveyDto.getTitle())
        .description(newSurveyDto.getDescription())
        .build());
  }

  public void deleteSurveyById(final long surveyId) {
    surveyRepository.deleteById(surveyId);
  }

  /**
   * @param nameId
   * @return
   */
  public Survey createNewSurveyVersion(final String nameId) {

    final List<Survey> surveys = this.surveyRepository.findByNameIdOrderByVersionDesc(nameId);

    if (surveys == null || surveys.isEmpty()) {
      throw new IllegalArgumentException("No survey found for nameId: " + nameId);
    }

    if (surveys.get(0).getReleaseStatus() != ReleaseStatusType.RELEASED) {
      throw new ConflictException("Current survey with nameId: " + nameId + " is not released.");
    }

    final Survey currentRelease = surveys.get(0);

    final List<Question> copiedQuestions = copyQuestions(currentRelease.getQuestions());

    return this.surveyRepository.save(currentRelease.toBuilder()
        .id(null)
        .createdAt(null)
        .version(currentRelease.getVersion() + 1)
        .releaseStatus(ReleaseStatusType.NEW)
        .questions(copiedQuestions)
        .build());
  }

  /**
   * @param
   * @param data
   * @return
   */
  public Question updateQuestion(final QuestionDto data) {

    final Question question = this.questionRepository.findById(data.getId()).get();

    /*
     * We have to check, if the specified ID in data is actually part of the current survey entity. So
     * we have to look for it and because of that, we do not need to request the question entity via the
     * question repository.
     */
    final SearchResult result = searchQuestion(question);

    // Survey must not be released
    if (result.getSurvey().getReleaseStatus() == ReleaseStatusType.RELEASED) {
      throw new ConflictException(
          "Related survey with nameId: " + result.getSurvey().getNameId()
              + " got released already.");
    }

    final int currentRanking = question.getRanking();

    final QuestionType dataType = data.getType();

    if (!question.getType().equals(dataType)) {
      throw new IllegalArgumentException(
          "The question type does not match the expected question type. Expected: "
              + question.getType() + "; Received: " + dataType);
    }

    if (data.getOrder() >= result.getContainer().getQuestions().size()) {
      throw new IllegalArgumentException(
          "The specified order is greater than the possible value. Expected: "
              + result.getContainer().getQuestions().size() + " Received: " + data.getOrder());
    }

    updateQuestionData(question, data);

    // Persist updates
    final Question updatedQuestion = this.questionRepository.save(question);

    // Update ranking of siblings if required
    if (question.getRanking() != currentRanking) {
      updateRankings(result.getContainer(), updatedQuestion);
    }

    return updatedQuestion;
  }

  public Question addQuestion(final Long containerId, final QuestionDto data) {

    final Container container = this.containerRepository.findById(containerId).get();

    return null;
  }

  private Question createQuestion(final Container parentContainer, final BooleanQuestionDto data) {

    return this.questionRepository.save(BooleanQuestion.builder()
        .question(data.getQuestion())
        .ranking(parentContainer.getQuestions().size())
        .defaultAnswer(data.getDefaultAnswer())
        .build());
  }

  private Question createQuestion(final Container parentContainer, final ChoiceQuestionDto data) {

    return this.questionRepository.save(ChoiceQuestion.builder()
        .question(data.getQuestion())
        .ranking(parentContainer.getQuestions().size())
        .build());
  }

  private Question createQuestion(final Container parentContainer, final RangeQuestionDto data) {

    return this.questionRepository.save(RangeQuestion.builder()
        .question(data.getQuestion())
        .ranking(parentContainer.getQuestions().size())
        .defaultAnswer(data.getDefaultValue())
        .minText(data.getMinText())
        .maxText(data.getMaxText())
        .minValue(data.getMinValue())
        .maxValue(data.getMaxValue())
        .build());
  }

  private Question createQuestion(final Container parentContainer, final TextQuestionDto data) {

    return this.questionRepository.save(TextQuestion.builder()
        .question(data.getQuestion())
        .ranking(parentContainer.getQuestions().size())
        .multiline(data.isMultiline())
        .length(data.getLength())
        .build());
  }

  private Question createQuestion(final Container parentContainer,
      final ChecklistQuestionDto data) {

    return this.questionRepository.save(ChecklistQuestion.builder()
        .question(data.getQuestion())
        .ranking(parentContainer.getQuestions().size())
        .build());
  }

  /**
   * @param
   * @param updatedQuestion
   */
  private void updateRankings(final Container container, final Question updatedQuestion) {

    for (int i = 0; i < container.getQuestions().size(); i++) {

      final Question currentSibling = container.getQuestions().get(i);

      // Skip updating updatedQuestion
      if (currentSibling.getId().equals(updatedQuestion.getId())) {
        continue;
      }

      if (currentSibling.getRanking() <= updatedQuestion.getRanking()) {

        currentSibling.setRanking(i);
        this.questionRepository.save(currentSibling);

      } else {
        break;
      }
    }
  }

  private SearchResult searchQuestion(final Question question) {

    if (question == null) {
      return null;
    }

    final Optional<Container> originContainerOp =
        this.containerRepository.findByQuestionsIn(Collections.singleton(question));

    if (originContainerOp.isEmpty()) {
      throw new IllegalStateException(
          "Unexpected state. No container found containing question id: " + question.getId());
    }

    Optional<Container> containerOp = originContainerOp;

    // Iterate up the tree to the root and check if question belongs to expected survey
    while (containerOp.isPresent()) {

      final Container container = containerOp.get();
      if (container.getParent() != null) {

        containerOp =
            this.containerRepository
                .findByQuestionsIn(Collections.singleton(container.getParent()));

      } else if (container.getType() != ContainerType.SURVEY) {

        throw new IllegalStateException(
            "Expected survey to be root of the tree. Found root container id: " + container
                .getId());

      } else {

        return SearchResult.builder()
            .container(originContainerOp.get())
            .survey((Survey) container)
            .build();
      }
    }
    return null;


  }

  private void updateQuestionData(final Question question, final QuestionDto data) {

    question.setQuestion(data.getQuestion());
    question.setRanking(data.getOrder());

    switch (question.getType()) {
      case BOOL:
        updateQuestion((BooleanQuestion) question, (BooleanQuestionDto) data);
        break;
      case CHECKLIST:
        // nothing special
        break;
      case CHECKLIST_ENTRY:
        updateQuestion((ChecklistEntry) question, (ChecklistEntryDto) data);
        break;
      case CHOICE:
        updateQuestion((ChoiceQuestion) question, (ChoiceQuestionDto) data);
        break;
      case RANGE:
        updateQuestion((RangeQuestion) question, (RangeQuestionDto) data);
        break;
      case TEXT:
        updateQuestion((TextQuestion) question, (TextQuestionDto) data);
        break;
      default:
        break;
    }
  }

  private void updateQuestion(final BooleanQuestion question, final BooleanQuestionDto data) {

    question.setDefaultAnswer(data.getDefaultAnswer());
  }

  private void updateQuestion(final ChecklistEntry question, final ChecklistEntryDto data) {

    question.setDefaultAnswer(data.getDefaultAnswer());
  }

  private void updateQuestion(final ChoiceQuestion question, final ChoiceQuestionDto data) {

    // FIXME Update answers

    final Optional<Answer> answerOp = question.getAnswers().stream()
        .filter(p -> p.getId().equals(data.getDefaultAnswer()))
        .reduce((a, b) -> {
          throw new IllegalStateException("Multiple elements: " + a + ", " + b);
        });

    if (answerOp.isEmpty()) {
      throw new IllegalArgumentException(
          "Specified default answer ID does not exists in the question scope. Specified: " +
              data.getDefaultAnswer());
    }

    question.setDefaultAnswer(answerOp.get());
    question.setMultiple(data.isMultiple());
  }

  private void updateQuestion(final RangeQuestion question, final RangeQuestionDto data) {

    question.setDefaultAnswer(data.getDefaultValue());
    question.setMaxText(data.getMaxText());
    question.setMaxValue(data.getMaxValue());
    question.setMinText(data.getMinText());
    question.setMinValue(data.getMinValue());
  }

  private void updateQuestion(final TextQuestion question, final TextQuestionDto data) {

    question.setLength(data.getLength());
    question.setMultiline(data.isMultiline());
  }

  private List<Question> copyQuestions(final List<Question> questions) {

    if (questions == null) {
      return null;
    }

    final List<Question> copies = new ArrayList<>(questions.size());

    for (final Question question : questions) {

      switch (question.getType()) {
        case BOOL:
          copies.add(copyQuestion((BooleanQuestion) question));
          break;
        case CHECKLIST:
          copies.add(copyQuestion((ChecklistQuestion) question));
          break;
        case CHOICE:
          copies.add(copyQuestion((ChoiceQuestion) question));
          break;
        case RANGE:
          copies.add(copyQuestion((RangeQuestion) question));
          break;
        case TEXT:
          copies.add(copyQuestion((TextQuestion) question));
          break;
        default:
          break;

      }
    }

    return copies;
  }

  private ChecklistQuestion copyQuestion(final ChecklistQuestion question) {

    final List<ChecklistEntry> entries = new ArrayList<>();

    for (final ChecklistEntry entry : question.getEntries()) {
      entries.add(copyQuestion(entry));
    }

    return this.questionRepository.save(question.toBuilder()
        .id(null)
        .createdAt(null)
        .entries(entries)
        .build());
  }

  private ChecklistEntry copyQuestion(final ChecklistEntry entry) {

    return this.questionRepository.save(entry.toBuilder()
        .id(null)
        .createdAt(null)
        .build());
  }

  private BooleanQuestion copyQuestion(final BooleanQuestion question) {

    final BooleanContainer container = copyContainer(question.getContainer());

    return this.questionRepository.save(question.toBuilder()
        .id(null)
        .createdAt(null)
        .container(container)
        .build());
  }

  private Question copyQuestion(final RangeQuestion question) {

    return this.questionRepository.save(question.toBuilder()
        .id(null)
        .createdAt(null)
        .build());
  }

  private TextQuestion copyQuestion(final TextQuestion question) {

    return this.questionRepository.save(question.toBuilder()
        .id(null)
        .createdAt(null)
        .build());
  }

  private ChoiceQuestion copyQuestion(final ChoiceQuestion question) {

    final List<Answer> answers = copyAnswers(question.getAnswers());

    final ChoiceContainer container = copyContainer(question.getContainer(), answers);

    return this.questionRepository.save(question.toBuilder()
        .id(null)
        .createdAt(null)
        .answers(answers)
        .container(container)
        .build());
  }

  private List<Answer> copyAnswers(final List<Answer> answers) {

    if (answers == null || answers.isEmpty()) {
      return null;
    }

    final List<Answer> copies = new ArrayList<>(answers.size());

    for (final Answer answer : answers) {

      copies.add(this.answerRepository.save(answer.toBuilder()
          .id(null)
          .createdAt(null)
          .build()));
    }

    return copies;
  }

  private BooleanContainer copyContainer(final BooleanContainer container) {

    if (container == null) {
      return null;
    }

    final List<Question> questions = copyQuestions(container.getQuestions());

    return this.containerRepository.save(container.toBuilder()
        .id(null)
        .createdAt(null)
        .questions(questions)
        .build());
  }

  private ChoiceContainer copyContainer(final ChoiceContainer container,
      final List<Answer> answersCopy) {

    if (container == null) {
      return null;
    }

    final List<Question> questions = copyQuestions(container.getQuestions());

    List<Answer> dependsOn = null;

    if (container.getDependsOn() != null && !container.getDependsOn().isEmpty()) {

      dependsOn = answersCopy.stream()
          .filter(p -> container.getDependsOn().stream()
              .anyMatch(m -> m.getValue().equals(p.getValue())))
          .collect(Collectors.toList());
    }

    return this.containerRepository.save(container.toBuilder()
        .id(null)
        .createdAt(null)
        .questions(questions)
        .dependsOn(dependsOn)
        .build());
  }

}
