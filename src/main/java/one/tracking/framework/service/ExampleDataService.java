/**
 *
 */
package one.tracking.framework.service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.event.ApplicationStartedEvent;
import org.springframework.context.annotation.Profile;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;
import org.springframework.util.Assert;
import one.tracking.framework.config.ReminderConfig;
import one.tracking.framework.entity.User;
import one.tracking.framework.entity.Verification;
import one.tracking.framework.entity.VerificationState;
import one.tracking.framework.entity.meta.Answer;
import one.tracking.framework.entity.meta.IntervalType;
import one.tracking.framework.entity.meta.ReleaseStatusType;
import one.tracking.framework.entity.meta.ReminderType;
import one.tracking.framework.entity.meta.Survey;
import one.tracking.framework.entity.meta.container.BooleanContainer;
import one.tracking.framework.entity.meta.container.ChoiceContainer;
import one.tracking.framework.entity.meta.question.BooleanQuestion;
import one.tracking.framework.entity.meta.question.ChecklistEntry;
import one.tracking.framework.entity.meta.question.ChecklistQuestion;
import one.tracking.framework.entity.meta.question.ChoiceQuestion;
import one.tracking.framework.entity.meta.question.NumberQuestion;
import one.tracking.framework.entity.meta.question.Question;
import one.tracking.framework.entity.meta.question.RangeQuestion;
import one.tracking.framework.entity.meta.question.TextQuestion;
import one.tracking.framework.repo.AnswerRepository;
import one.tracking.framework.repo.ContainerRepository;
import one.tracking.framework.repo.QuestionRepository;
import one.tracking.framework.repo.SurveyRepository;
import one.tracking.framework.repo.UserRepository;
import one.tracking.framework.repo.VerificationRepository;
import one.tracking.framework.support.JWTHelper;

/**
 * FIXME DEV only
 *
 * @author Marko Voß
 *
 */
@Service
@Profile("example")
public class ExampleDataService {

  private static final Logger LOG = LoggerFactory.getLogger(ExampleDataService.class);

  @Autowired
  private AnswerRepository answerRepository;

  @Autowired
  private QuestionRepository questionRepository;

  @Autowired
  private ContainerRepository containerRepository;

  @Autowired
  private SurveyRepository surveyRepository;

  @Autowired
  private VerificationRepository verificationRepository;

  @Autowired
  private UserRepository userRepository;

  @Autowired
  private JWTHelper jwtHelper;

  @Autowired
  private ReminderConfig reminderConfig;

  @EventListener
  void handleEvent(final ApplicationStartedEvent event) {

    LOG.info("Creating example data...");

    createAccount();
    createRegularSurvey(createBasicSurvey());

    this.reminderConfig.updateSchedule();

    LOG.info("Creation of example data finished.");
  }

  private void createAccount() {

    this.verificationRepository.save(Verification.builder()
        .email("foo@example.com")
        .hash("example")
        .state(VerificationState.VERIFIED)
        .createdBy("TEST")
        .build());

    final User user = this.userRepository.save(User.builder()
        .build());

    final String token = this.jwtHelper.createJWT(user.getId(), 365 * 24 * 60 * 60);

    LOG.info("Token for example user: {}", token);
  }

  private Survey createBasicSurvey() {

    int order = 0;
    final List<Question> questions = new ArrayList<>(12);

    final String s256 =
        "Lorem ipsum dolor sit amet, consetetur sadipscing elitr, sed diam nonumy eirmod tempor invidunt ut labore et dolore magna aliquyam erat, sed diam voluptua. At vero eos et accusam et justo duo dolores et ea rebum. Stet clita kasd gubergren, no sea takimata!";
    final String s64 = "Lorem ipsum dolor sit amet, consetetur sadipscing elitr, sed dia";
    final String s32 = "Lorem ipsum dolor sit amet, cons";

    // TEST MAX TEXT
    questions.add(createChoiceQuestion(
        s256,
        order++, false, false,
        Arrays.asList(
            s64,
            s64,
            s64,
            s64)));

    questions.add(createRangeQuestion(
        s256,
        order++,
        false,
        0,
        10,
        0,
        s32,
        s32));

    // 1
    questions.add(createChoiceQuestion(
        "What is your age?",
        order++, true, false,
        Arrays.asList(
            "18 - 24",
            "25 - 44",
            "45 - 64",
            "65 +")));

    // 2
    questions.add(createChoiceQuestion(
        "Gender?",
        order++, true, false,
        Arrays.asList(
            "Male",
            "Female")));

    // 3
    questions.add(createChoiceQuestion(
        "What is your marital status?",
        order++, true, false,
        Arrays.asList(
            "Married or civil partnership",
            "Living with partner",
            "Single")));

    // 4
    questions.add(createChoiceQuestion(
        "Are you in employment?",
        order++, true, false,
        Arrays.asList(
            "Full time employment",
            "Part time employment",
            "Retired",
            "Not in employment"),
        Arrays.asList(
            "Full time employment",
            "Part time employment"),
        Collections.singletonList(createTextQuestion("Type of employment:", 0, false, 256))));

    // 5
    questions.add(createChoiceQuestion(
        "In general, how do you find your job?",
        order++, true, false,
        Arrays.asList(
            "Not at all stressful",
            "Mildly stressful",
            "Moderately stressful",
            "Very stressful",
            "Extremely stressful")));

    // 6
    questions.add(createBoolQuestion(
        "Did you get a flu vaccination in the past year?", order++));

    // 7
    questions.add(createBoolQuestion(
        "In general, do you have any health problems that require you to limit your activities?", order++,
        true,
        Collections
            .singletonList(createTextQuestion("If yes, what health problem limits your activities?", 0, false, 256))));

    // 8
    questions.add(createBoolQuestion(
        "Do you suffer from heart disease?", order++, null, null));

    // 9
    questions.add(createBoolQuestion(
        "Do you suffer from diabetes?", order++, null, null));

    // 10
    questions.add(createBoolQuestion(
        "Do you suffer from lung disease / asthma?", order++, null, null));

    // 11
    questions.add(createBoolQuestion(
        "Do you suffer from kidney disease?", order++, null, null));

    // 12
    questions.add(createBoolQuestion(
        "Do you smoke?", order++, null, null));

    return this.surveyRepository.save(Survey.builder()
        .questions(questions)
        .nameId("BASIC")
        .title(s32)
        .description(s256)
        .intervalType(IntervalType.NONE)
        .reminderType(ReminderType.NONE)
        .releaseStatus(ReleaseStatusType.RELEASED)
        .build());
  }

  private Survey createRegularSurvey(final Survey dependsOn) {

    int order = 0;
    final List<Question> questions = new ArrayList<>(12);

    // 13
    questions.add(createChoiceQuestion(
        "Under each heading, please tick the ONE box that best describes your health TODAY:",
        order++, true, false,
        Arrays.asList(
            "I have no problems walking about",
            "I have some problems walking about",
            "I have a lot of problems walking about")));

    // 14
    questions.add(createChoiceQuestion(
        "Looking after myself, please tick the ONE box that best describes your health TODAY:",
        order++, true, false,
        Arrays.asList(
            "I have no problems washing or dressing myself",
            "I have some problems washing or dressing myself",
            "I have a lot of problems washing or dressing myself")));

    // 15
    questions.add(createChoiceQuestion(
        "Doing usual activities please tick the ONE box that best describes your health TODAY (for example, work, study, housework, family or leisure activities):",
        order++, true, false,
        Arrays.asList(
            "I have no problems doing my usual activities",
            "I have some problems doing my usual activities",
            "I have a lot of problems doing my usual activities")));

    // 16
    questions.add(createChoiceQuestion(
        "Having pain/ discomfort:",
        order++, true, false,
        Arrays.asList(
            "I have no pain or discomfort",
            "I have some pain or discomfort",
            "I have a lot of pain or discomfort")));

    // 17
    questions.add(createChoiceQuestion(
        "Feeling worried, sad or unhappy:",
        order++, true, false,
        Arrays.asList(
            "I am not worried, sad or unhappy",
            "I am a bit worried, sad or unhappy",
            "I am very worries, sad or unhappy")));

    // 18
    questions.add(createChoiceQuestion(
        "How often do you feel lonely?",
        order++, true, false,
        Arrays.asList(
            "Often / always",
            "Some of the time",
            "Occasionally",
            "Hardly ever",
            "Never")));

    // 19
    questions.add(createChoiceQuestion(
        "How would you rate your general health TODAY:",
        order++, true, false,
        Arrays.asList(
            "Excellent",
            "Very good",
            "Good",
            "Fair",
            "Poor")));

    // 20
    questions.add(createBoolQuestion(
        "Do you think you currently have/ had a COVID-19 infection?",
        order++));

    // 21
    questions.add(createBoolQuestion(
        "Have you been tested for a COVID-19 infection?",
        order++, null, null));

    // 22
    questions.add(createBoolQuestion(
        "Have you been hospitalised for a COVID-19 infection?",
        order++,
        true,
        Collections.singletonList(createNumberQuestion("How many days were you sick?", 0, 0, null, null))));

    // 23
    questions.add(createChecklistQuestion(
        "To what degree have you experienced the following symptoms in the last 7 days:",
        order++,
        Arrays.asList(
            createChecklistEntry("Headache", 0),
            createChecklistEntry("Muscle pain/aches", 1),
            createChecklistEntry("Difficulty breathing", 2),
            createChecklistEntry("Fever/ high temperature", 3),
            createChecklistEntry("Sore throat", 4),
            createChecklistEntry("Dry cough", 5),
            createChecklistEntry("Wet cough", 6),
            createChecklistEntry("I felt physically exhausted", 7),
            createChecklistEntry("loss of smell and taste", 8))));

    // 24
    questions.add(createRangeQuestion(
        "How much have you been concerned about the corona crisis in the past 7 days?",
        order++,
        true,
        1, 10,
        null,
        "Not concerned", "Extremely concerned"));

    // 25
    questions.add(createRangeQuestion(
        "How would you rate your quality of life over the last 7 days?",
        order++,
        true,
        1, 10,
        null,
        "Terrible", "Outstanding"));

    // 26
    questions.add(createRangeQuestion(
        "How socially isolated have you felt in the last 7 days?",
        order++,
        true,
        1, 10,
        null,
        "Not socially isolated", "Extremely socially isolated"));

    // 27
    questions.add(createRangeQuestion(
        "If you suffer from depression, how much has this condition been affected by the COVID-19 crisis in the last 7 days?",
        order++,
        true,
        1, 10,
        null,
        "Not at all", "Extremely depressed"));

    // 28
    questions.add(createRangeQuestion(
        "If you suffer from anxiety, how much has this condition been affected by the COVID-19 crisis in the last 7 days?",
        order++,
        true,
        1, 10,
        null,
        "Not at all", "Extremely anxious"));

    // 29
    questions.add(createChoiceQuestion(
        "How would you rate your overall mental health?",
        order++, true, false,
        Arrays.asList(
            "Excellent",
            "Very good",
            "Good",
            "Fair",
            "Poor")));

    // 30
    questions.add(createBoolQuestion(
        "Has the COVID-19 crisis affected the way you normally source your food/ medicines?",
        order++));

    // 31
    questions.add(createChoiceQuestion(
        "Where do you currently shop for your groceries?",
        order++, true, false,
        Arrays.asList(
            "Local supermarket",
            "Online shopping",
            "Local corner shops",
            "Farmer’s market")));

    // 32
    questions.add(createChoiceQuestion(
        "How healthy is your eating pattern compared to the period before the COVID-19 crisis?",
        order++, true, false,
        Arrays.asList(
            "Much less healthy",
            "Just as healthy",
            "Healthier")));

    // 33
    questions.add(createChoiceQuestion(
        "How would you rate your overall diet?",
        order++, true, false,
        Arrays.asList(
            "Excellent",
            "Very good",
            "Good",
            "Fair",
            "Poor")));

    // 34
    questions.add(createChoiceQuestion(
        "How many hours of moderately intense exercise did you do before the Covid-19 crisis?",
        order++, true, false,
        Arrays.asList(
            "Less than 1 hour",
            "1-2 hours",
            "More than 2 hours")));

    // 35
    questions.add(createBoolQuestion(
        "Has the COVID-19 crisis affected your ability to exercise?",
        order++,
        true,
        Collections.singletonList(createTextQuestion("If yes, how?", 0, false, 256))));

    // 36
    questions.add(createTextQuestion(
        "Has the COVID-19 crisis changed the way that you use social media?",
        order++, true, 256));

    return this.surveyRepository.save(Survey.builder()
        .dependsOn(dependsOn)
        .questions(questions)
        .nameId("REGULAR")
        .title("Regular survey")
        .releaseStatus(ReleaseStatusType.RELEASED)
        .intervalStart(Instant.parse("2020-05-18T00:00:00Z"))
        .intervalType(IntervalType.WEEKLY)
        .intervalValue(1)
        .reminderType(ReminderType.AFTER_DAYS)
        .reminderValue(2)
        .build());
  }

  public Answer createAnswer(final String answer) {
    return this.answerRepository.save(Answer.builder()
        .value(answer)
        .build());
  }

  public Question createChoiceQuestion(
      final String question,
      final int order,
      final boolean optional,
      final boolean multiple,
      final List<String> answers) {

    return createChoiceQuestion(question, order, optional, multiple, answers, null, null);
  }

  public Question createChoiceQuestion(
      final String question,
      final int order,
      final boolean optional,
      final boolean multiple,
      final List<String> answers,
      final List<String> dependsOn,
      final List<Question> questions) {

    Assert.notEmpty(answers, "Answers must not be null or empty.");

    final List<Answer> answerEntities = answers.stream().map(f -> createAnswer(f)).collect(Collectors.toList());

    ChoiceQuestion parent = this.questionRepository.save(ChoiceQuestion.builder()
        .question(question)
        .ranking(order)
        .answers(answerEntities)
        .multiple(multiple)
        .optional(optional)
        .build());

    if (questions != null && !questions.isEmpty()) {

      final List<Answer> dependsOnAnswers = dependsOn == null || dependsOn.isEmpty() ? null
          : answerEntities.stream().filter(p -> dependsOn.contains(p.getValue())).collect(Collectors.toList());

      final ChoiceContainer container = this.containerRepository.save(ChoiceContainer.builder()
          .dependsOn(dependsOnAnswers)
          .questions(questions)
          .parent(parent)
          .build());

      parent.setContainer(container);
      parent = this.questionRepository.save(parent);
    }

    return parent;
  }

  public BooleanQuestion createBoolQuestion(
      final String question,
      final int order) {

    return createBoolQuestion(question, order, null, null);
  }

  public BooleanQuestion createBoolQuestion(
      final String question,
      final int order,
      final Boolean dependsOn,
      final List<Question> questions) {

    BooleanQuestion parent = this.questionRepository.save(BooleanQuestion.builder()
        .question(question)
        .ranking(order)
        .optional(true)
        .build());

    if (questions != null && !questions.isEmpty()) {

      final BooleanContainer container = this.containerRepository.save(BooleanContainer.builder()
          .questions(questions)
          .dependsOn(dependsOn)
          .parent(parent)
          .build());

      parent.setContainer(container);
      parent = this.questionRepository.save(parent);
    }

    return parent;
  }

  public ChecklistEntry createChecklistEntry(
      final String question,
      final int order) {

    return this.questionRepository.save(ChecklistEntry.builder()
        .question(question)
        .ranking(order)
        .optional(true)
        .build());
  }

  public Question createTextQuestion(
      final String question,
      final int order,
      final boolean multiline,
      final int length) {

    final TextQuestion parent = this.questionRepository.save(TextQuestion.builder()
        .question(question)
        .multiline(multiline)
        .ranking(order)
        .length(length)
        .optional(true)
        .build());

    return parent;
  }

  public Question createChecklistQuestion(
      final String question,
      final int order,
      final List<ChecklistEntry> entries) {

    if (entries == null || entries.isEmpty())
      throw new IllegalArgumentException("Entries must not be null or empty.");

    return this.questionRepository.save(ChecklistQuestion.builder()
        .question(question)
        .entries(entries)
        .ranking(order)
        .optional(true)
        .build());
  }

  public Question createRangeQuestion(
      final String question,
      final int order,
      final boolean optional,
      final int minValue, final int maxValue,
      final Integer defaultValue,
      final String minText, final String maxText) {

    final RangeQuestion parent = this.questionRepository.save(RangeQuestion.builder()
        .question(question)
        .minValue(minValue)
        .maxValue(maxValue)
        .defaultAnswer(defaultValue)
        .minText(minText)
        .maxText(maxText)
        .ranking(order)
        .optional(optional)
        .build());

    return parent;
  }

  public Question createNumberQuestion(
      final String question,
      final int order,
      final Integer minValue, final Integer maxValue,
      final Integer defaultValue) {

    final NumberQuestion parent = this.questionRepository.save(NumberQuestion.builder()
        .question(question)
        .minValue(minValue)
        .maxValue(maxValue)
        .defaultAnswer(defaultValue)
        .ranking(order)
        .optional(true)
        .build());

    return parent;
  }
}
