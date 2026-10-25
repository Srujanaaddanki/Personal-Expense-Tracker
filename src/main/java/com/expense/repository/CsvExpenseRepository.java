package com.expense.repository;

import com.expense.exception.PersistenceException;
import com.expense.model.Expense;
import com.expense.storage.CsvExpenseStorage;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

/**
 * Adapter implementing {@link ExpenseRepository} using local RFC 4180 CSV file storage.
 *
 * <p>Preserves atomic temporary file writes, whole-file validation, multiline support,
 * and transactional rollback guarantees provided by {@link CsvExpenseStorage}.
 */
public class CsvExpenseRepository implements ExpenseRepository {

    private final CsvExpenseStorage storage;
    private final Path dataFilePath;
    private final List<Expense> cache;
    private long nextId;
    private boolean loaded;

    public CsvExpenseRepository(CsvExpenseStorage storage, Path dataFilePath) {
        if (storage == null) {
            throw new IllegalArgumentException("Storage cannot be null.");
        }
        if (dataFilePath == null) {
            throw new IllegalArgumentException("Data file path cannot be null.");
        }
        this.storage = storage;
        this.dataFilePath = dataFilePath;
        this.cache = new ArrayList<>();
        this.nextId = 1L;
        this.loaded = false;
    }

    @Override
    public List<Expense> findAll() {
        List<Expense> loadedList = storage.loadExpenses(dataFilePath);
        cache.clear();

        long maxId = 0L;
        for (Expense e : loadedList) {
            cache.add(copy(e));
            if (e.getId() != null && e.getId() > maxId) {
                maxId = e.getId();
            }
        }

        this.nextId = (maxId == Long.MAX_VALUE) ? Long.MAX_VALUE : maxId + 1;
        this.loaded = true;

        List<Expense> result = new ArrayList<>(cache.size());
        for (Expense e : cache) {
            result.add(copy(e));
        }
        return Collections.unmodifiableList(result);
    }

    @Override
    public Optional<Expense> findById(Long id) {
        if (id == null || id <= 0) {
            return Optional.empty();
        }
        ensureLoaded();
        for (Expense e : cache) {
            if (id.equals(e.getId())) {
                return Optional.of(copy(e));
            }
        }
        return Optional.empty();
    }

    @Override
    public Expense save(Expense expense) {
        if (expense == null) {
            throw new IllegalArgumentException("Expense cannot be null.");
        }
        ensureLoaded();

        Expense toPersist;
        long allocatedId = nextId;

        if (expense.getId() == null) {
            if (nextId == Long.MAX_VALUE) {
                throw new IllegalStateException("Expense ID limit reached (Long.MAX_VALUE). Cannot add more expenses.");
            }
            toPersist = new Expense(allocatedId, expense.getDescription(), expense.getCategory(), expense.getAmount(), expense.getDate());
        } else {
            toPersist = copy(expense);
        }

        cache.add(toPersist);
        try {
            storage.saveExpenses(cache, dataFilePath);
            if (expense.getId() == null) {
                nextId = allocatedId + 1;
            } else if (toPersist.getId() >= nextId && toPersist.getId() < Long.MAX_VALUE) {
                nextId = toPersist.getId() + 1;
            }
            return copy(toPersist);
        } catch (PersistenceException e) {
            cache.remove(cache.size() - 1);
            throw e;
        }
    }

    @Override
    public Optional<Expense> update(Expense expense) {
        if (expense == null || expense.getId() == null) {
            return Optional.empty();
        }
        ensureLoaded();

        int targetIndex = -1;
        for (int i = 0; i < cache.size(); i++) {
            if (expense.getId().equals(cache.get(i).getId())) {
                targetIndex = i;
                break;
            }
        }

        if (targetIndex == -1) {
            return Optional.empty();
        }

        Expense prev = cache.get(targetIndex);
        Expense updated = copy(expense);
        cache.set(targetIndex, updated);

        try {
            storage.saveExpenses(cache, dataFilePath);
            return Optional.of(copy(updated));
        } catch (PersistenceException e) {
            cache.set(targetIndex, prev);
            throw e;
        }
    }

    @Override
    public boolean deleteById(Long id) {
        if (id == null || id <= 0) {
            return false;
        }
        ensureLoaded();

        int targetIndex = -1;
        for (int i = 0; i < cache.size(); i++) {
            if (id.equals(cache.get(i).getId())) {
                targetIndex = i;
                break;
            }
        }

        if (targetIndex == -1) {
            return false;
        }

        Expense removed = cache.remove(targetIndex);
        try {
            storage.saveExpenses(cache, dataFilePath);
            return true;
        } catch (PersistenceException e) {
            cache.add(targetIndex, removed);
            throw e;
        }
    }

    @Override
    public String getStorageLocation() {
        return dataFilePath.toString();
    }

    public Path getDataFilePath() {
        return dataFilePath;
    }

    public CsvExpenseStorage getStorage() {
        return storage;
    }

    /**
     * Synchronizes the repository's cache with an externally loaded expense list.
     *
     * @param loadedList List of loaded expenses
     */
    public void setExpenses(List<Expense> loadedList) {
        if (loadedList == null) {
            cache.clear();
            this.nextId = 1L;
            this.loaded = true;
            return;
        }

        cache.clear();
        long maxId = 0L;
        for (Expense e : loadedList) {
            cache.add(copy(e));
            if (e.getId() != null && e.getId() > maxId) {
                maxId = e.getId();
            }
        }
        this.nextId = (maxId == Long.MAX_VALUE) ? Long.MAX_VALUE : maxId + 1;
        this.loaded = true;
    }

    private void ensureLoaded() {
        if (!loaded) {
            findAll();
        }
    }

    private Expense copy(Expense original) {
        return new Expense(
                original.getId(),
                original.getDescription(),
                original.getCategory(),
                original.getAmount(),
                original.getDate()
        );
    }
}
