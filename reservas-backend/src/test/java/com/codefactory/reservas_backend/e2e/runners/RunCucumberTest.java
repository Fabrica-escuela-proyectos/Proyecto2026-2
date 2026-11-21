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
 * Excluye los escenarios @rate-limit (necesitan otro límite de intentos) y @pendiente (pasos aún sin implementar).
 */
@Suite
@IncludeEngines("cucumber")
@SelectPackages("features")
@ConfigurationParameter(key = GLUE_PROPERTY_NAME, value = "com.codefactory.reservas_backend.e2e")
@ConfigurationParameter(key = PLUGIN_PROPERTY_NAME,
        value = "net.serenitybdd.cucumber.core.plugin.SerenityReporterParallel,pretty")
@ConfigurationParameter(key = FILTER_TAGS_PROPERTY_NAME,
        value = "(@HU01 or @HU02 or @HU03 or @HU04) and not @rate-limit and not @pendiente")
public class RunCucumberTest {
}
