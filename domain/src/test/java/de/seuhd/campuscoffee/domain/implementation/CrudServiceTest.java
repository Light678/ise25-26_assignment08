package de.seuhd.campuscoffee.domain.implementation;

import de.seuhd.campuscoffee.domain.exceptions.DuplicationException;
import de.seuhd.campuscoffee.domain.exceptions.NotFoundException;
import de.seuhd.campuscoffee.domain.model.objects.DomainModel;
import de.seuhd.campuscoffee.domain.ports.data.CrudDataService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for {@link CrudServiceImpl}.
 */
@ExtendWith(MockitoExtension.class)
class CrudServiceTest {

    @Mock
    private CrudDataService<DummyDomain, Long> dataService;

    private TestCrudService crudService;

    @BeforeEach
    void setUp() {
        crudService = new TestCrudService(dataService);
    }

    @Test
    void clearDelegatesToDataService() {
        crudService.clear();
        verify(dataService).clear();
    }

    @Test
    void getAllDelegatesToDataService() {
        List<DummyDomain> list = List.of(new DummyDomain(1L), new DummyDomain(2L));
        when(dataService.getAll()).thenReturn(list);

        List<DummyDomain> result = crudService.getAll();

        assertSame(list, result);
        verify(dataService).getAll();
    }

    @Test
    void getByIdDelegatesToDataService() {
        DummyDomain entity = new DummyDomain(1L);
        when(dataService.getById(1L)).thenReturn(entity);

        DummyDomain result = crudService.getById(1L);

        assertSame(entity, result);
        verify(dataService).getById(1L);
    }

    @Test
    void upsertCreateWhenIdIsNullDoesNotValidateExistence() {
        DummyDomain toCreate = new DummyDomain(null);
        DummyDomain saved = new DummyDomain(10L);
        when(dataService.upsert(toCreate)).thenReturn(saved);

        DummyDomain result = crudService.upsert(toCreate);

        assertSame(saved, result);
        verify(dataService, never()).getById(any());
        verify(dataService).upsert(toCreate);
    }

    @Test
    void upsertUpdateWhenIdIsSetValidatesExistenceThenUpserts() {
        DummyDomain toUpdate = new DummyDomain(5L);
        when(dataService.getById(5L)).thenReturn(toUpdate);
        when(dataService.upsert(toUpdate)).thenReturn(toUpdate);

        DummyDomain result = crudService.upsert(toUpdate);

        assertSame(toUpdate, result);
        verify(dataService).getById(5L);
        verify(dataService).upsert(toUpdate);
    }

    @Test
    void upsertUpdateWhenEntityMissingPropagatesNotFound() {
        DummyDomain toUpdate = new DummyDomain(99L);
        when(dataService.getById(99L)).thenThrow(new NotFoundException(DummyDomain.class, 99L));

        assertThrows(NotFoundException.class, () -> crudService.upsert(toUpdate));

        verify(dataService).getById(99L);
        verify(dataService, never()).upsert(any());
    }

    @Test
    void upsertWhenDuplicationOccursPropagatesDuplicationException() {
        DummyDomain toCreate = new DummyDomain(null);
        when(dataService.upsert(toCreate)).thenThrow(new DuplicationException(DummyDomain.class, "name", "x"));

        assertThrows(DuplicationException.class, () -> crudService.upsert(toCreate));

        verify(dataService).upsert(toCreate);
    }

    @Test
    void deleteDelegatesToDataService() {
        crudService.delete(7L);
        verify(dataService).delete(7L);
    }

    /**
     * Minimal concrete subclass for testing the abstract base class.
     */
    static class TestCrudService extends CrudServiceImpl<DummyDomain, Long> {

        private final CrudDataService<DummyDomain, Long> data;

        TestCrudService(CrudDataService<DummyDomain, Long> data) {
            super(DummyDomain.class);
            this.data = data;
        }

        @Override
        protected CrudDataService<DummyDomain, Long> dataService() {
            return data;
        }
    }

    /**
     * Dummy domain object used to test CrudServiceImpl.
     * Assumes DomainModel requires at least getId().
     */
    static class DummyDomain implements DomainModel<Long> {
        private final Long id;

        DummyDomain(Long id) {
            this.id = id;
        }

        @Override
        public Long getId() {
            return id;
        }
    }
}
