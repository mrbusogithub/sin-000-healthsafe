package co.wethinkcode.healthsafe.application;

import co.wethinkcode.healthsafe.domain.AlertLevel;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class AlertLevelServiceTest {

    private AlertLevelService service;

    @BeforeEach
    void setUp() {
        service = new AlertLevelService();
    }

    @Test
    void initialAlertLevelIsZero() {

        assertEquals(
                0,
                service.getCurrentLevel().value()
        );
    }

    @Test
    void acceptsMinimumAlertLevel() {

        AlertLevel result = service.updateLevel(0);

        assertEquals(0, result.value());
        assertEquals(0, service.getCurrentLevel().value());
    }

    @Test
    void acceptsMaximumAlertLevel() {

        AlertLevel result = service.updateLevel(8);

        assertEquals(8, result.value());
        assertEquals(8, service.getCurrentLevel().value());
    }

    @Test
    void rejectsLevelBelowMinimum() {

        assertThrows(
                IllegalArgumentException.class,
                () -> service.updateLevel(-1)
        );

        assertEquals(0, service.getCurrentLevel().value());
    }

    @Test
    void rejectsLevelAboveMaximum() {

        assertThrows(
                IllegalArgumentException.class,
                () -> service.updateLevel(9)
        );

        assertEquals(0, service.getCurrentLevel().value());
    }

    @Test
    void rejectsMissingLevel() {

        assertThrows(
                IllegalArgumentException.class,
                () -> service.updateLevel(null)
        );

        assertEquals(0, service.getCurrentLevel().value());
    }

    @Test
    void allowsLevelToBeUpdatedMoreThanOnce() {

        service.updateLevel(3);
        service.updateLevel(7);

        assertEquals(7, service.getCurrentLevel().value());
    }
}