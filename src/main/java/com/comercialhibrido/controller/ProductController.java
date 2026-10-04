package com.comercialhibrido.controller;

import com.comercialhibrido.domain.entity.Product;
import com.comercialhibrido.repository.CompanyRepository;
import com.comercialhibrido.repository.ProductRepository;
import com.comercialhibrido.security.JwtService;
import com.comercialhibrido.security.Roles;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Catálogo de la empresa. El bot solo ofrece los productos marcados como disponibles.
 */
@RestController
@RequestMapping("/api/products")
@RequiredArgsConstructor
public class ProductController {

    private static final String AUTH = "authenticatedUser";

    private final ProductRepository productRepository;
    private final CompanyRepository companyRepository;

    @GetMapping
    public List<ProductResponse> listar(@RequestAttribute(AUTH) JwtService.JwtPayload user) {
        return productRepository.findByCompanyIdOrderByNameAsc(user.companyId()).stream()
            .map(ProductResponse::from)
            .toList();
    }

    @PostMapping
    public ResponseEntity<ProductResponse> crear(
        @RequestAttribute(AUTH) JwtService.JwtPayload user,
        @Valid @RequestBody ProductRequest request
    ) {
        Roles.requireAdmin(user);
        Product product = new Product();
        product.setCompany(companyRepository.getReferenceById(user.companyId()));
        request.applyTo(product);
        return ResponseEntity.status(HttpStatus.CREATED).body(ProductResponse.from(productRepository.save(product)));
    }

    @PutMapping("/{id}")
    public ProductResponse actualizar(
        @RequestAttribute(AUTH) JwtService.JwtPayload user,
        @PathVariable("id") UUID id,
        @Valid @RequestBody ProductRequest request
    ) {
        Roles.requireAdmin(user);
        Product product = propio(id, user);
        request.applyTo(product);
        return ProductResponse.from(productRepository.saveAndFlush(product));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> eliminar(@RequestAttribute(AUTH) JwtService.JwtPayload user, @PathVariable("id") UUID id) {
        Roles.requireAdmin(user);
        productRepository.delete(propio(id, user));
        return ResponseEntity.noContent().build();
    }

    private Product propio(UUID id, JwtService.JwtPayload user) {
        return productRepository.findByIdAndCompanyId(id, user.companyId())
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Producto no encontrado"));
    }

    public record ProductRequest(
        @NotBlank(message = "El nombre es obligatorio") @Size(max = 150) String name,
        @Size(max = 80) String category,
        @Size(max = 100) String material,
        @Size(max = 4_000) String description,
        @NotNull(message = "El precio es obligatorio")
        @DecimalMin(value = "0", message = "El precio no puede ser negativo")
        @Digits(integer = 10, fraction = 2) BigDecimal price,
        @Size(max = 10) String currency,
        @Min(0) @Max(365) Integer deliveryTimeDays,
        @Min(0) @Max(120) Integer warrantyMonths,
        Boolean available
    ) {
        void applyTo(Product p) {
            p.setName(name.strip());
            p.setCategory(blankToNull(category));
            p.setMaterial(blankToNull(material));
            p.setDescription(blankToNull(description));
            p.setPrice(price);
            p.setCurrency(currency == null || currency.isBlank() ? "USD" : currency.strip().toUpperCase());
            p.setDeliveryTimeDays(deliveryTimeDays);
            p.setWarrantyMonths(warrantyMonths);
            p.setAvailable(available == null || available);
        }

        private static String blankToNull(String v) {
            return v == null || v.isBlank() ? null : v.strip();
        }
    }

    public record ProductResponse(
        UUID id,
        String name,
        String category,
        String material,
        String description,
        BigDecimal price,
        String currency,
        Integer deliveryTimeDays,
        Integer warrantyMonths,
        boolean available,
        Instant updatedAt
    ) {
        static ProductResponse from(Product p) {
            return new ProductResponse(p.getId(), p.getName(), p.getCategory(), p.getMaterial(), p.getDescription(),
                p.getPrice(), p.getCurrency(), p.getDeliveryTimeDays(), p.getWarrantyMonths(), p.isAvailable(),
                p.getUpdatedAt());
        }
    }
}
