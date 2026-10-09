package com.codefactory.reservas_backend.provider.application;

import java.util.List;

/** Una página de negocios (numeración desde 0). */
public record BusinessPage(List<BusinessInfo> items, int page, int size, long totalElements, int totalPages) {
}
