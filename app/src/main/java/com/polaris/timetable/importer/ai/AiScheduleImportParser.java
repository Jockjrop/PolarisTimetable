package com.polaris.timetable.importer.ai;

import java.util.Collections;

/** Entry point for text -> extracted JSON -> DTO -> validated import candidate. */
public final class AiScheduleImportParser {
    private final AiScheduleTextExtractor extractor;
    private final AiScheduleJsonParser jsonParser;
    private final AiScheduleValidator validator;

    public AiScheduleImportParser() {
        this(new AiScheduleTextExtractor(), new AiScheduleJsonParser(),
                new AiScheduleValidator());
    }

    AiScheduleImportParser(AiScheduleTextExtractor extractor,
                           AiScheduleJsonParser jsonParser,
                           AiScheduleValidator validator) {
        this.extractor = extractor;
        this.jsonParser = jsonParser;
        this.validator = validator;
    }

    public AiScheduleImportResult parse(String aiText) {
        AiScheduleTextExtractor.Result extraction = extractor.extract(aiText);
        if (!extraction.isSuccess()) {
            return new AiScheduleImportResult.Failure(
                    Collections.singletonList(extraction.error));
        }

        AiScheduleJsonParser.Result parsing = jsonParser.parse(extraction.json);
        if (!parsing.isSuccess()) {
            return new AiScheduleImportResult.Failure(parsing.errors);
        }

        AiScheduleValidator.Result validation = validator.validate(parsing.data);
        if (!validation.isSuccess()) {
            return new AiScheduleImportResult.Failure(validation.errors);
        }
        return new AiScheduleImportResult.Success(parsing.data, validation.warnings);
    }
}
