package com.starsoft.voint.rag;

import java.util.List;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import com.starsoft.voint.common.exception.NotFoundException;
import com.starsoft.voint.rag.dto.RagCategoryCreateRequest;
import com.starsoft.voint.rag.dto.RagCategoryResponse;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class RagCategoryService {

    private final RagCategoryRepository repository;

    @Transactional(readOnly = true)
    public List<RagCategoryResponse> list(UUID tenantId) {
        return repository.findByTenantIdOrderByNameAsc(tenantId).stream()
                .map(RagCategoryResponse::from).toList();
    }

    @Transactional
    public RagCategoryResponse create(UUID tenantId, RagCategoryCreateRequest request) {
        String name = request.name().trim();
        if (repository.existsByTenantIdAndNameIgnoreCase(tenantId, name)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "'" + name + "' başlığı artıq var.");
        }
        RagCategory saved = repository.save(RagCategory.builder().tenantId(tenantId).name(name).build());
        return RagCategoryResponse.from(saved);
    }

    /**
     * Removes the heading only - any document already filed under this name keeps its category
     * text and still groups together on the screen, it just stops being one of the "always
     * shown even when empty" checklist entries.
     */
    @Transactional
    public void delete(UUID tenantId, UUID id) {
        RagCategory category = repository.findById(id)
                .orElseThrow(() -> NotFoundException.of("Başlıq", id));
        if (!tenantId.equals(category.getTenantId())) {
            throw NotFoundException.of("Başlıq", id);
        }
        repository.delete(category);
    }
}
