package com.comercialhibrido.dto;

import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record TomarControlRequest(
    @NotNull UUID salespersonId
) {}