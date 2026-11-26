package com.codefactory.reservas_backend.e2e.questions;

import net.serenitybdd.screenplay.Question;
import net.serenitybdd.screenplay.rest.questions.LastResponse;

/** Pregunta sobre la antelación mínima que devolvió la API (HU-08). */
public final class LaAntelacion {

    private LaAntelacion() {
    }

    public static Question<Integer> enHoras() {
        return Question.about("la antelación mínima en horas")
                .answeredBy(actor -> LastResponse.received().answeredBy(actor).jsonPath().getInt("hours"));
    }
}
