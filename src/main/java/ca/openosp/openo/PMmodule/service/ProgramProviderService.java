package ca.openosp.openo.PMmodule.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ca.openosp.openo.PMmodule.dao.ProgramProviderDAO;

/**
 * Service layer for managing program provider associations in the OpenO EMR Program Management module.
 *
 * <p>This service provides transactional business logic for program provider operations, acting as an
 * intermediary between the web layer and the data access layer. Program providers represent healthcare
 * providers who are associated with specific programs within the healthcare facility, enabling program-based
 * access control and workflow management.</p>
 *
 * <p>The service ensures proper transaction management for program provider operations and delegates
 * data access to the {@link ProgramProviderDAO}.</p>
 *
 * @see ProgramProviderDAO
 * @see ca.openosp.openo.PMmodule.model.ProgramProvider
 * @since 2026-01-24
 */
@Service
public class ProgramProviderService {
    @Autowired
    private ProgramProviderDAO programProviderDao;

    /**
     * Deletes a program provider association by its unique identifier.
     *
     * <p>This method removes a provider's association with a specific program. The operation is
     * executed within a write transaction to ensure data consistency. This is typically used when
     * a healthcare provider is being removed from a program or when cleaning up program assignments.</p>
     *
     * @param id Long the unique identifier of the program provider association to delete
     * @throws org.springframework.dao.DataAccessException if the database operation fails
     */
    @Transactional(readOnly = false)
    public void deleteProgramProvider(Long id) {
        programProviderDao.deleteProgramProvider(id);
    }
}
