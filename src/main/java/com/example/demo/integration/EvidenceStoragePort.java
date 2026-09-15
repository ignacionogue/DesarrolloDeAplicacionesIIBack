package com.example.demo.integration;

/**
 * Abstrae el mecanismo de almacenamiento de archivos de evidencias
 * (fotografias). La implementacion concreta (Azure Blob Storage u otra) la
 * define el integrante de Azure; el resto del modulo solo conoce esta
 * interfaz y trabaja con referencias opacas (storageReference).
 */
public interface EvidenceStoragePort {

    /** Devuelve una URL (temporal o publica, segun la implementacion) para acceder al archivo referenciado. */
    String resolveAccessUrl(String storageReference);
}
