package com.codefactory.reservas_backend.e2e.runners;

import org.junit.platform.suite.api.ConfigurationParameter;
import org.junit.platform.suite.api.IncludeEngines;
import org.junit.platform.suite.api.SelectPackages;
import org.junit.platform.suite.api.Suite;

import static io.cucumber.junit.platform.engine.Constants.FILTER_TAGS_PROPERTY_NAME;
import static io.cucumber.junit.platform.engine.Constants.GLUE_PROPERTY_NAME;
import static io.cucumber.junit.platform.engine.Constants.PLUGIN_PROPERTY_NAME;

/**
 * Ejecutor de las pruebas E2E. El nombre termina en "Test" para que Surefire lo tome.
 * Excluye @rate-limit (necesita otro límite de intentos), @pendiente (pasos sin implementar) y @hallazgo
 * (diferencias entre la HU y la API que el equipo debe resolver).
 */
@Suite
@IncludeEngines("cucumber")
@SelectPackages("features")
@ConfigurationParameter(key = GLUE_PROPERTY_NAME, value = "com.codefactory.reservas_backend.e2e")
@ConfigurationParameter(key = PLUGIN_PROPERTY_NAME,
        value = "net.serenitybdd.cucumber.core.plugin.SerenityReporterParallel,pretty")
@ConfigurationParameter(key = FILTER_TAGS_PROPERTY_NAME,
        value = "not @rate-limit and not @pendiente and not @hallazgo")
public class RunCucumberTest {
}
