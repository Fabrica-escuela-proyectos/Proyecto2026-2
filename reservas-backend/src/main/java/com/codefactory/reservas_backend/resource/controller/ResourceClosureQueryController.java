package com.codefactory.reservas_backend.resource.controller;

import com.codefactory.reservas_backend.common.config.OpenApiTags;
import com.codefactory.reservas_backend.identity.application.IdentityService;
import com.codefactory.reservas_backend.identity.application.UserIdentity;
import com.codefactory.reservas_backend.resource.application.ClosureInfo;
import com.codefactory.reservas_backend.resource.application.ResourceClosureService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/** HU-21 - Consultar y eliminar los cierres puntuales de un recurso. Solo el PROVEEDOR dueño. */
@RestController
@Tag(name = OpenApiTags.RESOURCES)
@RequiredArgsConstructor
public class ResourceClosureQueryController {

    private final ResourceClosureService closureService;
    private final IdentityService identityService;

    @Operation(summary = "Listar los cierres programados de un recurso (HU-21)",
            description = "Solo el proveedor dueño. Cierres que aún no terminan, del más próximo al más lejano (hora de Bogotá).")
    @ApiResponse(responseCode = "200", description = "Cierres vigentes y futuros")
    @ApiResponse(responseCode = "403", description = "El recurso es de otro proveedor")
    @ApiResponse(responseCode = "404", description = "El recurso no existe")
    @GetMapping("/api/v1/resources/{resourceId}/closures")
    @PreAuthorize("hasRole('PROVEEDOR')")
    public ResponseEntity<List<ClosureInfo>> list(@PathVariable UUID resourceId) {
        return ResponseEntity.ok(closureService.listUpcoming(resourceId, currentUser()));
    }

    @Operation(summary = "Eliminar un cierre antes de que empiece (HU-21)",
            description = "Solo el proveedor dueño. El recurso vuelve a su horario habitual en ese periodo. Las reservas canceladas por el cierre no se restauran.")
    @ApiResponse(responseCode = "204", description = "Cierre eliminado")
    @ApiResponse(responseCode = "403", description = "El recurso es de otro proveedor")
    @ApiResponse(responseCode = "404", description = "El recurso o el cierre no existen")
    @ApiResponse(responseCode = "409", description = "El cierre ya comenzó")
    @DeleteMapping("/api/v1/resources/{resourceId}/closures/{closureId}")
    @PreAuthorize("hasRole('PROVEEDOR')")
    public ResponseEntity<Void> delete(@PathVariable UUID resourceId, @PathVariable UUID closureId,
                                       HttpServletRequest httpRequest) {
        closureService.delete(resourceId, closureId, currentUser(), httpRequest.getRemoteAddr());
        return ResponseEntity.noContent().build();
    }

    private UserIdentity currentUser() {
        return identityService.getCurrentUser()
                .orElseThrow(() -> new AccessDeniedException("No hay una sesión activa"));
    }
}
