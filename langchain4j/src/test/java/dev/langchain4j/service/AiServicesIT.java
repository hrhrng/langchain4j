package dev.langchain4j.service;

import static dev.langchain4j.data.message.SystemMessage.systemMessage;
import static dev.langchain4j.data.message.UserMessage.userMessage;
import static dev.langchain4j.model.openai.OpenAiChatModelName.GPT_4_O_MINI;
import static dev.langchain4j.service.AiServicesIT.Ingredient.OIL;
import static dev.langchain4j.service.AiServicesIT.Ingredient.PEPPER;
import static dev.langchain4j.service.AiServicesIT.Ingredient.SALT;
import static dev.langchain4j.service.AiServicesIT.IssueCategory.COMFORT_ISSUE;
import static dev.langchain4j.service.AiServicesIT.IssueCategory.MAINTENANCE_ISSUE;
import static dev.langchain4j.service.AiServicesIT.IssueCategory.OVERALL_EXPERIENCE_ISSUE;
import static dev.langchain4j.service.AiServicesIT.IssueCategory.SERVICE_ISSUE;
import static dev.langchain4j.service.AiServicesIT.Sentiment.POSITIVE;
import static java.time.Month.JULY;
import static java.util.Collections.singletonList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.data.MapEntry.entry;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;

import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.memory.ChatMemory;
import dev.langchain4j.memory.chat.MessageWindowChatMemory;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.input.structured.StructuredPrompt;
import dev.langchain4j.model.moderation.ModerationModel;
import dev.langchain4j.model.openai.OpenAiChatModel;
import dev.langchain4j.model.openai.OpenAiModerationModel;
import dev.langchain4j.model.output.TokenUsage;
import dev.langchain4j.model.output.structured.Description;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.function.BiFunction;
import java.util.function.UnaryOperator;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
@EnabledIfEnvironmentVariable(named = "OPENAI_API_KEY", matches = ".+")
public class AiServicesIT {

    @Spy
    ChatModel chatModel = OpenAiChatModel.builder()
            .baseUrl(System.getenv("OPENAI_BASE_URL"))
            .apiKey(System.getenv("OPENAI_API_KEY"))
            .organizationId(System.getenv("OPENAI_ORGANIZATION_ID"))
            .modelName(GPT_4_O_MINI)
            .temperature(0.0)
            .logRequests(true)
            .logResponses(true)
            .build();

    @Spy
    ChatMemory chatMemory = MessageWindowChatMemory.withMaxMessages(10);

    @Spy
    ModerationModel moderationModel = OpenAiModerationModel.builder()
            .baseUrl(System.getenv("OPENAI_BASE_URL"))
            .apiKey(System.getenv("OPENAI_API_KEY"))
            .organizationId(System.getenv("OPENAI_ORGANIZATION_ID"))
            .build();

    @AfterEach
    void afterEach() {
        verifyNoMoreInteractionsFor(chatModel);
        verifyNoMoreInteractions(chatMemory);
        verifyNoMoreInteractions(moderationModel);
    }

    public static void verifyNoMoreInteractionsFor(ChatModel model) {
        try {
            verify(model, atLeastOnce()).doChat(any());
        } catch (Throwable ignored) {
            // don't care if it was called or not
        }
        try {
            verify(model, atLeastOnce()).defaultRequestParameters();
        } catch (Throwable ignored) {
            // don't care if it was called or not
        }
        try {
            verify(model, atLeastOnce()).supportedCapabilities();
        } catch (Throwable ignored) {
            // don't care if it was called or not
        }
        try {
            verify(model, atLeastOnce()).listeners();
        } catch (Throwable ignored) {
            // don't care if it was called or not
        }
        try {
            verify(model, atLeastOnce()).provider();
        } catch (Throwable ignored) {
            // don't care if it was called or not
        }
        verifyNoMoreInteractions(model);
    }

    interface EggCounter {

        @UserMessage("Count the number of eggs mentioned in this sentence:\n|||{{it}}|||")
        int count(String sentence);
    }

    @Test
    void simple_instruction_with_primitive_return_type() {
        EggCounter eggCounter = AiServices.create(EggCounter.class, chatModel);

        String sentence = "I have ten eggs in my basket and three in my pocket.";

        int count = eggCounter.count(sentence);
        assertThat(count).isEqualTo(13);

        verify(chatModel)
                .chat(chatRequest("Count the number of eggs mentioned in this sentence:\n"
                        + "|||I have ten eggs in my basket and three in my pocket.|||\n"
                        + "You must answer strictly in the following format: integer number"));
    }

    interface Humorist {

        @UserMessage("Tell me a joke about {{it}}")
        String joke(String topic);
    }

    @Test
    void simple_instruction_with_single_argument() {

        Humorist humorist = AiServices.create(Humorist.class, chatModel);

        String joke = humorist.joke("AI");

        assertThat(joke).isNotBlank();

        verify(chatModel).chat(chatRequest("Tell me a joke about AI"));
    }

    interface DateTimeExtractor {

        @UserMessage("Extract date from {{it}}")
        LocalDate extractDateFrom(String text);

        @UserMessage("Extract time from {{it}}")
        LocalTime extractTimeFrom(String text);

        @UserMessage("Extract date and time from {{it}}")
        LocalDateTime extractDateTimeFrom(String text);
    }

    @Test
    void extract_date() {

        DateTimeExtractor dateTimeExtractor = AiServices.create(DateTimeExtractor.class, chatModel);

        String text =
                "The tranquility pervaded the evening of 1968, just fifteen minutes shy of midnight, following the celebrations of Independence Day.";

        LocalDate date = dateTimeExtractor.extractDateFrom(text);

        assertThat(date).isEqualTo(LocalDate.of(1968, JULY, 4));

        verify(chatModel)
                .chat(chatRequest("Extract date from " + text + "\n"
                        + "You must answer strictly in the following format: yyyy-MM-dd"));
    }

    @Test
    void extract_time() {

        DateTimeExtractor dateTimeExtractor = AiServices.create(DateTimeExtractor.class, chatModel);

        String text =
                "The tranquility pervaded the evening of 1968, just fifteen minutes shy of midnight, following the celebrations of Independence Day.";

        LocalTime time = dateTimeExtractor.extractTimeFrom(text);

        assertThat(time).isEqualTo(LocalTime.of(23, 45, 0));

        verify(chatModel)
                .chat(chatRequest("Extract time from " + text + "\n"
                        + "You must answer strictly in the following format: HH:mm:ss"));
    }

    @Test
    void extract_date_time() {

        DateTimeExtractor dateTimeExtractor = AiServices.create(DateTimeExtractor.class, chatModel);

        String text =
                "The tranquility pervaded the evening of 1968, just fifteen minutes shy of midnight, following the celebrations of Independence Day.";

        LocalDateTime dateTime = dateTimeExtractor.extractDateTimeFrom(text);

        assertThat(dateTime).isEqualTo(LocalDateTime.of(1968, JULY, 4, 23, 45, 0));

        verify(chatModel)
                .chat(chatRequest("Extract date and time from " + text + "\n"
                        + "You must answer strictly in the following format: yyyy-MM-ddTHH:mm:ss"));
    }

    enum Sentiment {
        POSITIVE,
        NEUTRAL,
        NEGATIVE
    }

    interface SentimentAnalyzer {

        @UserMessage("Analyze sentiment of:\n|||{{it}}|||")
        Sentiment analyzeSentimentOf(String text);
    }

    @Test
    void extract_enum() {

        SentimentAnalyzer sentimentAnalyzer = AiServices.create(SentimentAnalyzer.class, chatModel);

        String customerReview = "This LaptopPro X15 is wicked fast and that 4K screen is a dream.";

        Sentiment sentiment = sentimentAnalyzer.analyzeSentimentOf(customerReview);

        assertThat(sentiment).isEqualTo(POSITIVE);

        verify(chatModel)
                .chat(chatRequest("Analyze sentiment of:\n|||" + customerReview + "|||\n"
                        + "You must answer strictly with one of these enums:\n"
                        + "POSITIVE\n"
                        + "NEUTRAL\n"
                        + "NEGATIVE"));
    }

    public enum Weather {
        @Description("A clear day with bright sunlight and few or no clouds")
        SUNNY,
        @Description("The sky is covered with clouds with no rain, often creating a gray and overcast appearance")
        CLOUDY,
        @Description("Precipitation in the form of rain, with cloudy skies and wet conditions")
        RAINY,
        @Description("Snowfall occurs, covering the ground in white and creating cold, wintry conditions")
        SNOWY
    }

    interface WeatherForecastAnalyzer {

        @UserMessage("Analyze weather forecast for:\n|||{{it}}|||")
        Weather analyzeWeatherForecast(String forecast);
    }

    @Test
    void extract_single_enum_with_description() {

        WeatherForecastAnalyzer weatherForecastAnalyzer = AiServices.create(WeatherForecastAnalyzer.class, chatModel);

        String weatherForecast =
                "It will be cloudy and mostly rainy. No more rain early in the day but the sky remains overcast. Afternoon it is mostly cloudy. The sun will not be visible. The forecast has a moderate, 40% chance of Precipitation. Temperatures peaking at 17 °C.";

        Weather weather = weatherForecastAnalyzer.analyzeWeatherForecast(weatherForecast);

        assertThat(weather).isEqualTo(Weather.RAINY);

        verify(chatModel)
                .chat(
                        chatRequest(
                                "Analyze weather forecast for:\n" + "|||"
                                        + weatherForecast + "|||\n"
                                        + "You must answer strictly with one of these enums:\n"
                                        + "SUNNY - A clear day with bright sunlight and few or no clouds\n"
                                        + "CLOUDY - The sky is covered with clouds with no rain, often creating a gray and overcast appearance\n"
                                        + "RAINY - Precipitation in the form of rain, with cloudy skies and wet conditions\n"
                                        + "SNOWY - Snowfall occurs, covering the ground in white and creating cold, wintry conditions"));
    }

    public enum Ingredient {
        SALT,
        PEPPER,
        VINEGAR,
        OIL
    }

    interface IngredientsExtractor {

        @UserMessage("Analyze the following recipe:\n|||{{it}}|||")
        List<Ingredient> extractIngredients(String recipe);
    }

    @Test
    void extract_list_of_enums() {
        IngredientsExtractor ingredientsExtractor = AiServices.create(IngredientsExtractor.class, chatModel);

        String recipe = "Just mix some salt, pepper and oil in the bowl. That will be a basis for...";

        List<Ingredient> ingredients = ingredientsExtractor.extractIngredients(recipe);
        assertThat(ingredients).isEqualTo(Arrays.asList(SALT, PEPPER, OIL));

        verify(chatModel)
                .chat(chatRequest("Analyze the following recipe:\n" + "|||"
                        + recipe + "|||\n"
                        + "You must answer strictly with zero or more of these enums on a separate line:\n"
                        + "SALT\n"
                        + "PEPPER\n"
                        + "VINEGAR\n"
                        + "OIL"));
    }

    public enum IssueCategory {
        @Description(
                "The feedback mentions issues with the hotel's maintenance, such as air conditioning and plumbing problems")
        MAINTENANCE_ISSUE,
        @Description("The feedback mentions issues with the service provided, such as slow room service")
        SERVICE_ISSUE,
        @Description(
                "The feedback mentions issues affecting the comfort of the stay, such as uncomfortable room conditions")
        COMFORT_ISSUE,
        @Description("The feedback mentions issues with hotel facilities, such as problems with the bathroom plumbing")
        FACILITY_ISSUE,
        @Description("The feedback mentions issues with the cleanliness of the hotel, such as dust and stains")
        CLEANLINESS_ISSUE,
        @Description("The feedback mentions issues with internet connectivity, such as unreliable Wi-Fi")
        CONNECTIVITY_ISSUE,
        @Description(
                "The feedback mentions issues with the check-in process, such as it being tedious and time-consuming")
        CHECK_IN_ISSUE,
        @Description(
                "The feedback mentions a general dissatisfaction with the overall hotel experience due to multiple issues")
        OVERALL_EXPERIENCE_ISSUE
    }

    interface HotelReviewIssueAnalyzer {
        @UserMessage("Please analyse the following review: |||{{it}}|||")
        List<IssueCategory> analyzeReview(String review);
    }

    @Test
    void extract_list_of_enums_with_descriptions() {
        HotelReviewIssueAnalyzer hotelReviewIssueAnalyzer =
                AiServices.create(HotelReviewIssueAnalyzer.class, chatModel);

        String review = "Our stay at hotel was a mixed experience. The location was perfect, just a stone's throw away "
                + "from the beach, which made our daily outings very convenient. The rooms were spacious and well-decorated, "
                + "providing a comfortable and pleasant environment. However, we encountered several issues during our "
                + "stay. The air conditioning in our room was not functioning properly, making the nights quite uncomfortable. "
                + "Additionally, the room service was slow, and we had to call multiple times to get extra towels. Despite the "
                + "friendly staff and enjoyable breakfast buffet, these issues significantly impacted our stay.";

        List<IssueCategory> issueCategories = hotelReviewIssueAnalyzer.analyzeReview(review);
        assertThat(issueCategories)
                .isEqualTo(Arrays.asList(MAINTENANCE_ISSUE, SERVICE_ISSUE, COMFORT_ISSUE, OVERALL_EXPERIENCE_ISSUE));

        verify(chatModel)
                .chat(
                        chatRequest(
                                "Please analyse the following review: |||" + review + "|||\n"
                                        + "You must answer strictly with zero or more of these enums on a separate line:\n"
                                        + "MAINTENANCE_ISSUE - The feedback mentions issues with the hotel's maintenance, such as air conditioning and plumbing problems\n"
                                        + "SERVICE_ISSUE - The feedback mentions issues with the service provided, such as slow room service\n"
                                        + "COMFORT_ISSUE - The feedback mentions issues affecting the comfort of the stay, such as uncomfortable room conditions\n"
                                        + "FACILITY_ISSUE - The feedback mentions issues with hotel facilities, such as problems with the bathroom plumbing\n"
                                        + "CLEANLINESS_ISSUE - The feedback mentions issues with the cleanliness of the hotel, such as dust and stains\n"
                                        + "CONNECTIVITY_ISSUE - The feedback mentions issues with internet connectivity, such as unreliable Wi-Fi\n"
                                        + "CHECK_IN_ISSUE - The feedback mentions issues with the check-in process, such as it being tedious and time-consuming\n"
                                        + "OVERALL_EXPERIENCE_ISSUE - The feedback mentions a general dissatisfaction with the overall hotel experience due to multiple issues"));
    }

    interface MapExtractor {

        @UserMessage("Return a JSON map with the age of each person in the following text: {{it}}")
        Map<String, Integer> extractAges(String text);
    }

    @Test
    void should_extract_map() {

        MapExtractor mapExtractor = AiServices.create(MapExtractor.class, chatModel);

        String text = "Klaus is 42 and Francine is 47";

        Map<String, Integer> ages = mapExtractor.extractAges(text);

        assertThat(ages).containsExactly(entry("Klaus", 42), entry("Francine", 47));

        verify(chatModel)
                .chat(chatRequest("Return a JSON map with the age of each person in the following text: " + text));
    }

    record Address(Integer streetNumber, String street, String city) {}

    static record Person(String firstName, String lastName, LocalDate birthDate, Address address) {}

    interface PersonExtractor {

        @UserMessage("Extract information about a person from {{it}}")
        Person extractPersonFrom(String text);
    }

    @Test
    void should_extract_custom_POJO() {

        PersonExtractor personExtractor = AiServices.create(PersonExtractor.class, chatModel);

        String text = "In 1968, amidst the fading echoes of Independence Day, "
                + "a child named John arrived under the calm evening sky. "
                + "This newborn, bearing the surname Doe, marked the start of a new journey."
                + "He was welcomed into the world at 345 Whispering Pines Avenue,"
                + "a quaint street nestled in the heart of Springfield,"
                + "an abode that echoed with the gentle hum of suburban dreams and aspirations.";

        Person person = personExtractor.extractPersonFrom(text);

        assertThat(person.firstName).isEqualTo("John");
        assertThat(person.lastName).isEqualTo("Doe");
        assertThat(person.birthDate).isEqualTo(LocalDate.of(1968, JULY, 4));
        assertThat(person.address.streetNumber).isEqualTo(345);
        assertThat(person.address.street).isEqualTo("Whispering Pines Avenue");
        assertThat(person.address.city).isEqualTo("Springfield");

        verify(chatModel)
                .chat(chatRequest("Extract information about a person from " + text + "\n"
                        + "You must answer strictly in the following JSON format: {\n"
                        + "\"firstName\": (type: string),\n"
                        + "\"lastName\": (type: string),\n"
                        + "\"birthDate\": (type: date string (2023-12-31)),\n"
                        + "\"address\": (type: dev.langchain4j.service.AiServicesIT$Address: {\n"
                        + "\"streetNumber\": (type: integer),\n"
                        + "\"street\": (type: string),\n"
                        + "\"city\": (type: string)\n"
                        + "})\n"
                        + "}"));
    }

    @Test
    void should_extract_custom_POJO_with_explicit_json_response_format() {

        ChatModel chatModel = spy(OpenAiChatModel.builder()
                .baseUrl(System.getenv("OPENAI_BASE_URL"))
                .apiKey(System.getenv("OPENAI_API_KEY"))
                .organizationId(System.getenv("OPENAI_ORGANIZATION_ID"))
                .modelName(GPT_4_O_MINI)
                .responseFormat("json_object")
                .temperature(0.0)
                .logRequests(true)
                .logResponses(true)
                .build());

        PersonExtractor personExtractor = AiServices.create(PersonExtractor.class, chatModel);

        String text = "In 1968, amidst the fading echoes of Independence Day, "
                + "a child named John arrived under the calm evening sky. "
                + "This newborn, bearing the surname Doe, marked the start of a new journey."
                + "He was welcomed into the world at 345 Whispering Pines Avenue,"
                + "a quaint street nestled in the heart of Springfield,"
                + "an abode that echoed with the gentle hum of suburban dreams and aspirations.";

        Person person = personExtractor.extractPersonFrom(text);

        assertThat(person.firstName).isEqualTo("John");
        assertThat(person.lastName).isEqualTo("Doe");
        assertThat(person.birthDate).isEqualTo(LocalDate.of(1968, JULY, 4));
        assertThat(person.address.streetNumber).isEqualTo(345);
        assertThat(person.address.street).isEqualTo("Whispering Pines Avenue");
        assertThat(person.address.city).isEqualTo("Springfield");

        verify(chatModel)
                .chat(chatRequest("Extract information about a person from " + text + "\n"
                        + "You must answer strictly in the following JSON format: {\n"
                        + "\"firstName\": (type: string),\n"
                        + "\"lastName\": (type: string),\n"
                        + "\"birthDate\": (type: date string (2023-12-31)),\n"
                        + "\"address\": (type: dev.langchain4j.service.AiServicesIT$Address: {\n"
                        + "\"streetNumber\": (type: integer),\n"
                        + "\"street\": (type: string),\n"
                        + "\"city\": (type: string)\n"
                        + "})\n"
                        + "}"));
    }

    static record Recipe(
            String title,
            String description,
            @Description("each step should be described in 4 words, steps should rhyme") String[] steps,
            Integer preparationTimeMinutes) {}

    interface Chef {

        @UserMessage("Create recipe using only {{it}}")
        Recipe createRecipeFrom(String... ingredients);

        @UserMessage(fromResource = "chefs-prompt-based-on-ingredients.txt")
        Recipe createRecipeFromUsingResource(String... ingredients);

        @UserMessage(fromResource = "chefs-prompt-based-on-ingredients-in-root.txt")
        Recipe createRecipeFromUsingResourceInRoot(String... ingredients);

        @UserMessage(fromResource = "subdirectory/chefs-prompt-based-on-ingredients-in-subdirectory.txt")
        Recipe createRecipeFromUsingResourceInSubdirectory(String... ingredients);

        Recipe createRecipeFrom(CreateRecipePrompt prompt);

        @SystemMessage("You are very {{character}} chef")
        Recipe createRecipeFrom(@UserMessage CreateRecipePrompt prompt, @V("character") String character);

        @SystemMessage(fromResource = "chefs-prompt-system-message.txt")
        Recipe createRecipeFromUsingResource(@UserMessage CreateRecipePrompt prompt, @V("character") String character);
    }

    @Test
    void create_recipe_from_list_of_ingredients() {

        Chef chef = AiServices.create(Chef.class, chatModel);

        Recipe recipe = chef.createRecipeFrom("cucumber", "tomato", "feta", "onion", "olives");

        assertThat(recipe.title).isNotBlank();
        assertThat(recipe.description).isNotBlank();
        assertThat(recipe.steps).isNotEmpty();
        assertThat(recipe.preparationTimeMinutes).isPositive();

        verify(chatModel)
                .chat(chatRequest("Create recipe using only [cucumber, tomato, feta, onion, olives]\n"
                        + "You must answer strictly in the following JSON format: {\n"
                        + "\"title\": (type: string),\n"
                        + "\"description\": (type: string),\n"
                        + "\"steps\": (each step should be described in 4 words, steps should rhyme; type: array of string),\n"
                        + "\"preparationTimeMinutes\": (type: integer)\n"
                        + "}"));
    }

    @Test
    void create_recipe_from_list_of_ingredients_using_resource() {

        Chef chef = AiServices.create(Chef.class, chatModel);

        Recipe recipe = chef.createRecipeFromUsingResource("cucumber", "tomato", "feta", "onion", "olives");

        assertThat(recipe.title).isNotBlank();
        assertThat(recipe.description).isNotBlank();
        assertThat(recipe.steps).isNotEmpty();
        assertThat(recipe.preparationTimeMinutes).isPositive();

        verify(chatModel)
                .chat(chatRequest("Create recipe using only [cucumber, tomato, feta, onion, olives]\n"
                        + "You must answer strictly in the following JSON format: {\n"
                        + "\"title\": (type: string),\n"
                        + "\"description\": (type: string),\n"
                        + "\"steps\": (each step should be described in 4 words, steps should rhyme; type: array of string),\n"
                        + "\"preparationTimeMinutes\": (type: integer)\n"
                        + "}"));
    }

    @Test
    void create_recipe_from_list_of_ingredients_using_resource_in_root() {

        Chef chef = AiServices.create(Chef.class, chatModel);

        Recipe recipe = chef.createRecipeFromUsingResourceInRoot("cucumber", "tomato", "feta", "onion", "olives");

        assertThat(recipe.title).isNotBlank();
        assertThat(recipe.description).isNotBlank();
        assertThat(recipe.steps).isNotEmpty();
        assertThat(recipe.preparationTimeMinutes).isPositive();

        verify(chatModel)
                .chat(chatRequest("Create recipe using only [cucumber, tomato, feta, onion, olives]\n"
                        + "You must answer strictly in the following JSON format: {\n"
                        + "\"title\": (type: string),\n"
                        + "\"description\": (type: string),\n"
                        + "\"steps\": (each step should be described in 4 words, steps should rhyme; type: array of string),\n"
                        + "\"preparationTimeMinutes\": (type: integer)\n"
                        + "}"));
    }

    @Test
    void create_recipe_from_list_of_ingredients_using_resource_in_subdirectory() {

        Chef chef = AiServices.create(Chef.class, chatModel);

        Recipe recipe =
                chef.createRecipeFromUsingResourceInSubdirectory("cucumber", "tomato", "feta", "onion", "olives");

        assertThat(recipe.title).isNotBlank();
        assertThat(recipe.description).isNotBlank();
        assertThat(recipe.steps).isNotEmpty();
        assertThat(recipe.preparationTimeMinutes).isPositive();

        verify(chatModel)
                .chat(chatRequest("Create recipe using only [cucumber, tomato, feta, onion, olives]\n"
                        + "You must answer strictly in the following JSON format: {\n"
                        + "\"title\": (type: string),\n"
                        + "\"description\": (type: string),\n"
                        + "\"steps\": (each step should be described in 4 words, steps should rhyme; type: array of string),\n"
                        + "\"preparationTimeMinutes\": (type: integer)\n"
                        + "}"));
    }

    interface BadChef {
        String CHEFS_PROMPT_DOES_NOT_EXIST_TXT = "chefs-prompt-does-not-exist.txt";

        @UserMessage(fromResource = "chefs-prompt-does-not-exist.txt")
        Recipe createRecipeWithNonExistingResource(String... ingredients);

        @UserMessage(fromResource = "chefs-prompt-is-empty.txt")
        Recipe createRecipeWithEmptyResource(String... ingredients);

        @UserMessage(fromResource = "chefs-prompt-is-blank.txt")
        Recipe createRecipeWithBlankResource(String... ingredients);
    }

    @Test
    void should_fail_when_user_message_resource_is_not_found() {
        BadChef badChef = AiServices.create(BadChef.class, chatModel);

        assertThatThrownBy(() ->
                        badChef.createRecipeWithNonExistingResource("cucumber", "tomato", "feta", "onion", "olives"))
                .isInstanceOf(IllegalConfigurationException.class)
                .hasMessage("@UserMessage's resource '" + BadChef.CHEFS_PROMPT_DOES_NOT_EXIST_TXT + "' not found");
    }

    @Test
    void should_fail_when_user_message_resource_is_empty() {
        BadChef badChef = AiServices.create(BadChef.class, chatModel);

        assertThatThrownBy(() -> badChef.createRecipeWithEmptyResource("cucumber", "tomato", "feta", "onion", "olives"))
                .isInstanceOf(IllegalConfigurationException.class)
                .hasMessage("@UserMessage's template cannot be empty");
    }

    @Test
    void should_fail_when_user_message_resource_is_blank() {
        BadChef badChef = AiServices.create(BadChef.class, chatModel);

        assertThatThrownBy(() -> badChef.createRecipeWithBlankResource("cucumber", "tomato", "feta", "onion", "olives"))
                .isInstanceOf(IllegalConfigurationException.class)
                .hasMessage("@UserMessage's template cannot be empty");
    }

    @StructuredPrompt("Create a recipe of a {{dish}} that can be prepared using only {{ingredients}}")
    record CreateRecipePrompt(String dish, List<String> ingredients) {}

    @Test
    void create_recipe_using_structured_prompt() {

        Chef chef = AiServices.create(Chef.class, chatModel);

        CreateRecipePrompt prompt =
                new CreateRecipePrompt("salad", List.of("cucumber", "tomato", "feta", "onion", "olives"));

        Recipe recipe = chef.createRecipeFrom(prompt);

        assertThat(recipe.title).isNotBlank();
        assertThat(recipe.description).isNotBlank();
        assertThat(recipe.steps).isNotEmpty();
        assertThat(recipe.preparationTimeMinutes).isPositive();

        verify(chatModel)
                .chat(chatRequest(
                        "Create a recipe of a salad that can be prepared using only [cucumber, tomato, feta, onion, olives]\n"
                                + "You must answer strictly in the following JSON format: {\n"
                                + "\"title\": (type: string),\n"
                                + "\"description\": (type: string),\n"
                                + "\"steps\": (each step should be described in 4 words, steps should rhyme; type: array of string),\n"
                                + "\"preparationTimeMinutes\": (type: integer)\n"
                                + "}"));
    }

    @Test
    void create_recipe_using_structured_prompt_and_system_message() {

        Chef chef = AiServices.create(Chef.class, chatModel);

        CreateRecipePrompt prompt =
                new CreateRecipePrompt("salad", List.of("cucumber", "tomato", "feta", "onion", "olives"));

        Recipe recipe = chef.createRecipeFrom(prompt, "funny");

        assertThat(recipe.title).isNotBlank();
        assertThat(recipe.description).isNotBlank();
        assertThat(recipe.steps).isNotEmpty();
        assertThat(recipe.preparationTimeMinutes).isPositive();

        verify(chatModel)
                .chat(ChatRequest.builder()
                        .messages(
                                systemMessage("You are very funny chef"),
                                userMessage(
                                        "Create a recipe of a salad that can be prepared using only [cucumber, tomato, feta, onion, olives]\n"
                                                + "You must answer strictly in the following JSON format: {\n"
                                                + "\"title\": (type: string),\n"
                                                + "\"description\": (type: string),\n"
                                                + "\"steps\": (each step should be described in 4 words, steps should rhyme; type: array of string),\n"
                                                + "\"preparationTimeMinutes\": (type: integer)\n"
                                                + "}"))
                        .build());
    }

    @Test
    void create_recipe_using_structured_prompt_and_system_message_from_resource() {

        Chef chef = AiServices.create(Chef.class, chatModel);

        CreateRecipePrompt prompt =
                new CreateRecipePrompt("salad", List.of("cucumber", "tomato", "feta", "onion", "olives"));

        Recipe recipe = chef.createRecipeFromUsingResource(prompt, "funny");

        assertThat(recipe.title).isNotBlank();
        assertThat(recipe.description).isNotBlank();
        assertThat(recipe.steps).isNotEmpty();
        assertThat(recipe.preparationTimeMinutes).isPositive();

        verify(chatModel)
                .chat(ChatRequest.builder()
                        .messages(
                                systemMessage("You are very funny chef"),
                                userMessage(
                                        "Create a recipe of a salad that can be prepared using only [cucumber, tomato, feta, onion, olives]\n"
                                                + "You must answer strictly in the following JSON format: {\n"
                                                + "\"title\": (type: string),\n"
                                                + "\"description\": (type: string),\n"
                                                + "\"steps\": (each step should be described in 4 words, steps should rhyme; type: array of string),\n"
                                                + "\"preparationTimeMinutes\": (type: integer)\n"
                                                + "}"))
                        .build());
    }

    interface ProfessionalChef {

        @SystemMessage("You are a professional chef. You are friendly, polite and concise.")
        String answer(String question);
    }

    @Test
    void with_system_message() {

        ProfessionalChef chef = AiServices.create(ProfessionalChef.class, chatModel);

        String question = "How long should I grill chicken?";

        String answer = chef.answer(question);

        assertThat(answer).isNotBlank();

        verify(chatModel)
                .chat(ChatRequest.builder()
                        .messages(
                                systemMessage("You are a professional chef. You are friendly, polite and concise."),
                                userMessage(question))
                        .build());
    }

    interface Translator {

        @SystemMessage("You are a professional translator into {{language}}")
        @UserMessage("Translate the following text: {{text}}")
        String translate(@V("text") String text, @V("language") String language);
    }

    @Test
    void with_system_and_user_messages() {

        Translator translator = AiServices.create(Translator.class, chatModel);

        String text = "Hello, how are you?";

        String translation = translator.translate(text, "german");

        assertThat(translation).isEqualTo("Hallo, wie geht es dir?");

        verify(chatModel)
                .chat(ChatRequest.builder()
                        .messages(
                                systemMessage("You are a professional translator into german"),
                                userMessage("Translate the following text: Hello, how are you?"))
                        .build());
    }

    interface Summarizer {

        @SystemMessage("Summarize every message from user in {{n}} bullet points. Provide only bullet points.")
        List<String> summarize(@UserMessage String text, @V("n") int n);
    }

    @Test
    void with_system_message_and_user_message_as_argument() {

        Summarizer summarizer = AiServices.create(Summarizer.class, chatModel);

        String text = "AI, or artificial intelligence, is a branch of computer science that aims to create "
                + "machines that mimic human intelligence. This can range from simple tasks such as recognizing "
                + "patterns or speech to more complex tasks like making decisions or predictions.";

        List<String> bulletPoints = summarizer.summarize(text, 3);

        assertThat(bulletPoints).hasSize(3);

        verify(chatModel)
                .chat(ChatRequest.builder()
                        .messages(
                                systemMessage(
                                        "Summarize every message from user in 3 bullet points. Provide only bullet points."),
                                userMessage(text + "\nYou must put every item on a separate line."))
                        .build());
    }

    interface ChatWithModeration {

        @Moderate
        String chat(String message);
    }

    @Test
    void should_throw_when_text_is_flagged() {

        ChatWithModeration chatWithModeration = AiServices.builder(ChatWithModeration.class)
                .chatModel(chatModel)
                .moderationModel(moderationModel)
                .build();

        String message = "I WILL KILL YOU!!!";

        assertThatThrownBy(() -> chatWithModeration.chat(message))
                .isExactlyInstanceOf(ModerationException.class)
                .hasMessage("Text \"" + message + "\" violates content policy")
                .satisfies(e -> {
                    final var moderationException = (ModerationException) e;
                    final var moderation = moderationException.moderation();
                    assertThat(moderation.flagged()).isTrue();
                    assertThat(moderation.flaggedText()).contains("I WILL KILL YOU!!!");
                });

        verify(chatModel).chat(chatRequest(message));
        verify(moderationModel).moderate(singletonList(userMessage(message)));
    }

    @Test
    void should_not_throw_when_text_is_not_flagged() {

        ChatWithModeration chatWithModeration = AiServices.builder(ChatWithModeration.class)
                .chatModel(chatModel)
                .moderationModel(moderationModel)
                .build();

        String message = "I will hug them!";

        String response = chatWithModeration.chat(message);

        assertThat(response).isNotBlank();

        verify(chatModel).chat(chatRequest(message));
        verify(moderationModel).moderate(singletonList(userMessage(message)));
    }

    interface AssistantReturningResult {

        Result<String> chat(String userMessage);
    }

    @Test
    void should_return_result() {

        // given
        AssistantReturningResult assistant = AiServices.create(AssistantReturningResult.class, chatModel);

        String userMessage = "What is the capital of Germany?";

        // when
        Result<String> result = assistant.chat(userMessage);

        // then
        assertThat(result.content()).containsIgnoringCase("Berlin");

        TokenUsage tokenUsage = result.tokenUsage();
        assertThat(tokenUsage).isNotNull();
        assertThat(tokenUsage.inputTokenCount()).isPositive();
        assertThat(tokenUsage.outputTokenCount()).isPositive();
        assertThat(tokenUsage.totalTokenCount())
                .isEqualTo(tokenUsage.inputTokenCount() + tokenUsage.outputTokenCount());

        assertThat(result.sources()).isEmpty();

        verify(chatModel).chat(chatRequest(userMessage));
    }

    interface AssistantReturningResultWithPojo {

        Result<Booking> answer(String query);
    }

    static class Booking {

        String userId;
        String bookingId;
    }

    @Test
    void should_use_content_retriever_and_return_sources_inside_result_with_pojo() {

        // given
        AssistantReturningResultWithPojo assistant =
                AiServices.create(AssistantReturningResultWithPojo.class, chatModel);

        // when
        Result<Booking> result = assistant.answer("Give me an example of a booking");

        // then
        Booking booking = result.content();
        assertThat(booking.userId).isNotBlank();
        assertThat(booking.bookingId).isNotBlank();

        assertThat(result.tokenUsage()).isNotNull();
        assertThat(result.sources()).isEmpty();

        verify(chatModel)
                .chat(chatRequest("Give me an example of a booking\n"
                        + "You must answer strictly in the following JSON format: {\n"
                        + "\"userId\": (type: string),\n"
                        + "\"bookingId\": (type: string)\n"
                        + "}"));
    }

    @Test
    void should_rewrite_chat_request() {
        UserMessageTransformer requestTransformer = userMessage -> userMessage.replace("three", "four");

        EggCounter eggCounter = AiServices.builder(EggCounter.class)
                .chatModel(chatModel)
                .chatRequestTransformer(requestTransformer)
                .build();

        String sentence = "I have ten eggs in my basket and three in my pocket.";

        int count = eggCounter.count(sentence);
        assertThat(count).isEqualTo(14);

        verify(chatModel)
                .chat(chatRequest("Count the number of eggs mentioned in this sentence:\n"
                        + "|||I have ten eggs in my basket and four in my pocket.|||\n"
                        + "You must answer strictly in the following format: integer number"));
    }

    static ChatRequest chatRequest(String userMessage) {
        return ChatRequest.builder()
                .messages(dev.langchain4j.data.message.UserMessage.from(userMessage))
                .build();
    }

    @FunctionalInterface
    public interface UserMessageTransformer extends UnaryOperator<ChatRequest> {

        @Override
        default ChatRequest apply(ChatRequest chatRequest) {
            return chatRequest.messages().stream()
                    .filter(dev.langchain4j.data.message.UserMessage.class::isInstance)
                    .map(dev.langchain4j.data.message.UserMessage.class::cast)
                    .findFirst()
                    .map(userMessage -> {
                        String originalMessage = userMessage.singleText();
                        String transformedMessage = transformUserMessage(originalMessage);
                        if (transformedMessage == null || transformedMessage.equals(originalMessage)) {
                            return chatRequest; // No transformation needed
                        }
                        List<ChatMessage> messages = chatRequest.messages().stream()
                                .map(message -> message == userMessage ?
                                        dev.langchain4j.data.message.UserMessage.from(transformedMessage) :
                                        message)
                                .toList();
                        return ChatRequest.builder()
                                .messages(messages)
                                .parameters(chatRequest.parameters())
                                .build();
                    })
                    .orElse(chatRequest);
        }

        String transformUserMessage(String userMessage);
    }
}
