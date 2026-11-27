/*
 * SPDX-License-Identifier: GPL-2.0-or-later
 * Copyright (C) 2026 CARLOS Contributors
 */
package io.github.carlos_emr.carlos.providers.gate;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.carlos_emr.carlos.test.web.JspRenderServer;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Renders the second page of the ALPHA antenatal psychosocial form, the {@code success} result of
 * the {@code provider/ViewFormALPHAprint1} gate ({@link ViewFormALPHAprint12Action}).
 * 72fedd972c50 (#6305, #6146) replaced the nineteen question bullets, which had been stored as the
 * UTF-8 bytes of a replacement character (and so printed as a replacement mark, or as
 * U+00EF U+00BF U+00BD once Jasper read the encoding-less page as ISO-8859-1), with
 * {@code &#8226;}. The printed page must show each question after a real bullet (U+2022) and no
 * replacement character anywhere. Before #6336 {@code JspPageEncodingRegressionTest} read the JSP
 * source and the only packaged use of the route was an authorization sweep that checks the status.
 */
@Tag("integration")
@Tag("form")
class AlphaFormPrintJspIntegrationTest {
    private static final String PAGE = "/WEB-INF/jsp/provider/formALPHAprint1.jsp";
    private static final char BULLET = '\u2022';
    /** The nineteen questions, in page order, as the printed text reads after each bullet. */
    private static final List<String> QUESTIONS = List.of(
            "How many drinks of alcohol do you have per week?",
            "Are there times when you drink more than that?",
            "Do you or your partner use recreational drugs?",
            "Do you or your partner have a problem with alcohol or drugs?",
            "Consider CAGE (Cut down, Annoyed, Guilty, Eye opener)",
            "What was your parents' relationship like?",
            "Did your father ever scare or hurt your mother?",
            "Did your parents ever scare or hurt you?",
            "Were you ever sexually abused as a child?",
            "How do you and your partner solve arguments?",
            "Do you ever feel frightened by what your partner says or does?",
            "Have you ever been hit/pushed/slapped by a partner?",
            "Has your partner ever humiliated you or psychologically abused you in other ways?",
            "Have you ever been forced to have sex against your will?",
            "Do you/your partner have children not living with you? If so, why?",
            "Have you ever had involvement with a child protection agency (i.e., Children's Aid Society)?",
            "How were you disciplined as a child?",
            "How do you think you will discipline your child?",
            "How do you deal with your kids at home when they misbehave?");

    @TempDir
    static Path directory;
    private static JspRenderServer server;

    @BeforeAll
    static void startServer() throws Exception {
        server = JspRenderServer.builder(directory).jsp(PAGE).start();
    }

    @AfterAll
    static void stopServer() throws Exception {
        if (server != null) {
            server.close();
        }
    }

    @Test
    void shouldPrintEveryQuestionAfterABullet_asTheBulletCharacter() throws Exception {
        JspRenderServer.RenderedPage page = server.render(PAGE,
                request -> request.getSession().setAttribute("user", "999998")).assertOk();

        assertThat(page.header("Content-Type").orElse("")).containsIgnoringCase("charset=UTF-8");
        String text = page.document().body().text();
        assertThat(QUESTIONS).hasSize(19);
        for (String question : QUESTIONS) {
            assertThat(text).as(question).contains(BULLET + question);
        }
        assertThat((int) text.chars().filter(character -> character == BULLET).count()).isEqualTo(QUESTIONS.size());
        assertThat(page.body()).doesNotContain("\uFFFD", "\u00EF\u00BF\u00BD");
    }
}
