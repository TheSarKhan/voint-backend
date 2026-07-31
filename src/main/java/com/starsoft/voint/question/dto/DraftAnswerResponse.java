package com.starsoft.voint.question.dto;

import java.util.List;

/**
 * AI-nin təklif etdiyi cavab qaralaması.
 *
 * @param answer          təklif olunan mətn — operator redaktə edib saxlayır
 * @param usedKnowledge   qaralamanın söykəndiyi mövcud bilik bazası parçaları (qısa),
 *                        operator "bunu haradan götürdü" sualına baxa bilsin
 * @param missingFacts    AI-nin bilmədiyi və operatorun doldurmalı olduğu konkret məlumatlar
 *                        (qiymət, müddət, nömrə). Boş deyilsə ekran bunu açıq göstərməlidir.
 */
public record DraftAnswerResponse(
        String answer,
        List<String> usedKnowledge,
        List<String> missingFacts
) {
}
