package com.example.demo.controller;

import com.example.demo.exception.InvalidRequestException;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class EtagPreconditionsTest {
    @Test
    void parsesStrongWeakAndOptionalEtags() {
        assertNull(EtagPreconditions.parse(null));
        assertNull(EtagPreconditions.parse("  "));
        assertEquals(7L, EtagPreconditions.parse("7"));
        assertEquals(8L, EtagPreconditions.parse("\"8\""));
        assertEquals(9L, EtagPreconditions.parse("W/\"9\""));
        assertEquals("\"10\"", EtagPreconditions.value(10));
        assertThrows(InvalidRequestException.class, () -> EtagPreconditions.parse("W/not-a-number"));
    }
}
