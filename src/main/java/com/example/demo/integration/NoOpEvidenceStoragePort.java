package com.example.demo.integration;

import org.springframework.stereotype.Component;

/**
 * Implementacion PROVISIONAL: no hay storage real configurado todavia
 * (depende del integrante de Azure). Devuelve la referencia sin resolver,
 * solo para no romper el resto del modulo mientras eso se define.
 */
@Component
public class NoOpEvidenceStoragePort implements EvidenceStoragePort {

    @Override
    public String resolveAccessUrl(String storageReference) {
        return storageReference;
    }
}
