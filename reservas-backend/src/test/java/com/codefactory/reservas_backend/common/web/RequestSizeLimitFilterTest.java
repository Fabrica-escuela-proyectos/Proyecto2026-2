package com.codefactory.reservas_backend.common.web;

import jakarta.servlet.ServletException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** OWASP-02: el cuerpo de una petición no puede superar el tope configurado. */
class RequestSizeLimitFilterTest {

    private static final long MAX = 100;

    private RequestSizeLimitFilter filter;
    private MockHttpServletResponse response;

    @BeforeEach
    void setUp() {
        filter = new RequestSizeLimitFilter(MAX, new ObjectMapper());
        response = new MockHttpServletResponse();
    }

    private static MockHttpServletRequest post(byte[] body) {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/v1/users");
        request.setContent(body);
        return request;
    }

    /** Petición «chunked»: sin Content-Length declarado. */
    private static MockHttpServletRequest chunked(byte[] body) {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/v1/users") {
            @Override
            public long getContentLengthLong() {
                return -1;
            }
        };
        request.setContent(body);
        request.addHeader("Transfer-Encoding", "chunked");
        return request;
    }

    @Test
    void uncuerpoQueSuperaElTopeDeclaradoSeRechazaCon413SinLlamarALaCadena() throws Exception {
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(post(new byte[(int) MAX + 1]), response, chain);

        assertThat(response.getStatus()).isEqualTo(413);
        assertThat(response.getContentType()).startsWith("application/json");
        assertThat(response.getContentAsString(StandardCharsets.UTF_8))
                .contains("\"error\":\"PAYLOAD_TOO_LARGE\"")
                .contains("\"path\":\"/api/v1/users\"");
        assertThat(chain.getRequest()).as("la petición no debe llegar al controlador").isNull();
    }

    @Test
    void uncuerpoDentroDelTopeSigueSuCurso() throws Exception {
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(post(new byte[(int) MAX]), response, chain);

        assertThat(response.getStatus()).isEqualTo(200);
        assertThat(chain.getRequest()).isNotNull();
    }

    @Test
    void unaPeticionSinCuerpoSigueSuCurso() throws Exception {
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(new MockHttpServletRequest("GET", "/api/v1/businesses"), response, chain);

        assertThat(chain.getRequest()).isNotNull();
    }

    @Test
    void unCuerpoChunkedDentroDelTopeSeLeeCompleto() throws Exception {
        AtomicReference<byte[]> leido = new AtomicReference<>();
        MockFilterChain chain = new MockFilterChain(new jakarta.servlet.http.HttpServlet() {
            @Override
            protected void service(jakarta.servlet.http.HttpServletRequest req, jakarta.servlet.http.HttpServletResponse res)
                    throws IOException {
                leido.set(req.getInputStream().readAllBytes());
            }
        });

        filter.doFilter(chunked("x".repeat((int) MAX).getBytes(StandardCharsets.UTF_8)), response, chain);

        assertThat(leido.get()).hasSize((int) MAX);
    }

    @Test
    void unCuerpoChunkedQueSuperaElTopeFallaAlLeerlo() throws Exception {
        AtomicReference<Throwable> fallo = new AtomicReference<>();
        MockFilterChain chain = new MockFilterChain(new jakarta.servlet.http.HttpServlet() {
            @Override
            protected void service(jakarta.servlet.http.HttpServletRequest req, jakarta.servlet.http.HttpServletResponse res) {
                try {
                    req.getInputStream().readAllBytes();
                } catch (IOException e) {
                    fallo.set(e);
                }
            }
        });

        filter.doFilter(chunked(new byte[(int) MAX + 50]), response, chain);

        assertThat(fallo.get()).isInstanceOf(IOException.class).hasMessageContaining("tamaño máximo");
    }

    @Test
    void elLectorDeCaracteresTambienTieneElTope() throws Exception {
        AtomicReference<Throwable> fallo = new AtomicReference<>();
        MockFilterChain chain = new MockFilterChain(new jakarta.servlet.http.HttpServlet() {
            @Override
            protected void service(jakarta.servlet.http.HttpServletRequest req, jakarta.servlet.http.HttpServletResponse res) {
                try {
                    req.getReader().readLine();
                } catch (IOException e) {
                    fallo.set(e);
                }
            }
        });

        filter.doFilter(chunked(("y".repeat((int) MAX + 50)).getBytes(StandardCharsets.UTF_8)), response, chain);

        assertThat(fallo.get()).isInstanceOf(IOException.class);
    }

    @Test
    void elFiltroNoCapturaLaExcepcionDeLaCadenaDeOtroOrigen() {
        MockFilterChain chain = new MockFilterChain(new jakarta.servlet.http.HttpServlet() {
            @Override
            protected void service(jakarta.servlet.http.HttpServletRequest req, jakarta.servlet.http.HttpServletResponse res)
                    throws ServletException {
                throw new ServletException("falla de otro componente");
            }
        });

        assertThatThrownBy(() -> filter.doFilter(post(new byte[10]), response, chain))
                .isInstanceOf(ServletException.class);
    }
}
