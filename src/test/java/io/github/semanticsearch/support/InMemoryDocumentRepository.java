package io.github.semanticsearch.support;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.data.domain.*;
import org.springframework.data.repository.query.FluentQuery;

import io.github.semanticsearch.model.Document;
import io.github.semanticsearch.repository.DocumentRepository;

/**
 * Simple in-memory implementation of {@link DocumentRepository} for fast, isolated unit tests.
 * Only the methods that are used in tests are implemented; the rest throw
 * {@link UnsupportedOperationException} to surface unexpected usage.
 */
public class InMemoryDocumentRepository implements DocumentRepository {

  private final Map<UUID, Document> store = new ConcurrentHashMap<>();

  @Override
  public Optional<Document> findByContentHash(String contentHash) {
    return store.values().stream()
        .filter(doc -> Objects.equals(doc.getContentHash(), contentHash))
        .findFirst();
  }

  @Override
  public Page<Document> findByIndexed(boolean indexed, Pageable pageable) {
    List<Document> filtered =
        store.values().stream().filter(doc -> doc.isIndexed() == indexed).toList();
    return page(filtered, pageable);
  }

  @Override
  public Page<Document> findByTitleOrContentContainingIgnoreCase(String text, Pageable pageable) {
    String lowered = text.toLowerCase(Locale.ROOT);
    List<Document> filtered =
        store.values().stream()
            .filter(
                doc ->
                    doc.getTitle().toLowerCase(Locale.ROOT).contains(lowered)
                        || doc.getContent().toLowerCase(Locale.ROOT).contains(lowered))
            .toList();
    return page(filtered, pageable);
  }

  @Override
  public List<Document> findByMetadata(String key, String value) {
    return store.values().stream()
        .filter(doc -> value.equals(doc.getMetadata().get(key)))
        .collect(Collectors.toList());
  }

  @Override
  public Optional<Document> findByVectorId(String vectorId) {
    return store.values().stream()
        .filter(doc -> Objects.equals(vectorId, doc.getVectorId()))
        .findFirst();
  }

  @Override
  public <S extends Document> S save(S entity) {
    if (entity.getId() == null) {
      entity.setId(UUID.randomUUID());
    }
    store.put(entity.getId(), entity);
    return entity;
  }

  @Override
  public <S extends Document> List<S> saveAll(Iterable<S> entities) {
    List<S> saved = new ArrayList<>();
    for (S entity : entities) {
      saved.add(save(entity));
    }
    return saved;
  }

  @Override
  public Optional<Document> findById(UUID uuid) {
    return Optional.ofNullable(store.get(uuid));
  }

  @Override
  public boolean existsById(UUID uuid) {
    return store.containsKey(uuid);
  }

  @Override
  public List<Document> findAll() {
    return new ArrayList<>(store.values());
  }

  @Override
  public List<Document> findAllById(Iterable<UUID> uuids) {
    List<Document> result = new ArrayList<>();
    for (UUID id : uuids) {
      Document doc = store.get(id);
      if (doc != null) {
        result.add(doc);
      }
    }
    return result;
  }

  @Override
  public long count() {
    return store.size();
  }

  @Override
  public void deleteById(UUID uuid) {
    store.remove(uuid);
  }

  @Override
  public void delete(Document entity) {
    if (entity.getId() != null) {
      store.remove(entity.getId());
    }
  }

  @Override
  public void deleteAllById(Iterable<? extends UUID> uuids) {
    for (UUID id : uuids) {
      deleteById(id);
    }
  }

  @Override
  public void deleteAll(Iterable<? extends Document> entities) {
    for (Document doc : entities) {
      delete(doc);
    }
  }

  @Override
  public void deleteAll() {
    store.clear();
  }

  @Override
  public List<Document> findAll(Sort sort) {
    // Sorting is not critical for these tests; return unsorted list.
    return findAll();
  }

  @Override
  public Page<Document> findAll(Pageable pageable) {
    return page(findAll(), pageable);
  }

  @Override
  public void flush() {}

  @Override
  public <S extends Document> S saveAndFlush(S entity) {
    return save(entity);
  }

  @Override
  public <S extends Document> List<S> saveAllAndFlush(Iterable<S> entities) {
    return saveAll(entities);
  }

  @Override
  public void deleteAllInBatch(Iterable<Document> entities) {
    deleteAll(entities);
  }

  @Override
  public void deleteAllByIdInBatch(Iterable<UUID> uuids) {
    deleteAllById(uuids);
  }

  @Override
  public void deleteAllInBatch() {
    deleteAll();
  }

  @Override
  public Document getOne(UUID uuid) {
    return store.get(uuid);
  }

  @Override
  public Document getById(UUID uuid) {
    return store.get(uuid);
  }

  @Override
  public Document getReferenceById(UUID uuid) {
    return store.get(uuid);
  }

  @Override
  public <S extends Document> Optional<S> findOne(Example<S> example) {
    throw new UnsupportedOperationException("Example queries are not supported in memory.");
  }

  @Override
  public <S extends Document> List<S> findAll(Example<S> example) {
    throw new UnsupportedOperationException("Example queries are not supported in memory.");
  }

  @Override
  public <S extends Document> List<S> findAll(Example<S> example, Sort sort) {
    throw new UnsupportedOperationException("Example queries are not supported in memory.");
  }

  @Override
  public <S extends Document> Page<S> findAll(Example<S> example, Pageable pageable) {
    throw new UnsupportedOperationException("Example queries are not supported in memory.");
  }

  @Override
  public <S extends Document> long count(Example<S> example) {
    throw new UnsupportedOperationException("Example queries are not supported in memory.");
  }

  @Override
  public <S extends Document> boolean exists(Example<S> example) {
    throw new UnsupportedOperationException("Example queries are not supported in memory.");
  }

  @Override
  public <S extends Document, R> R findBy(
      Example<S> example, Function<FluentQuery.FetchableFluentQuery<S>, R> queryFunction) {
    throw new UnsupportedOperationException("Example queries are not supported in memory.");
  }

  private Page<Document> page(List<Document> source, Pageable pageable) {
    if (pageable == null) {
      return new PageImpl<>(source);
    }
    int start = (int) pageable.getOffset();
    int end = Math.min(start + pageable.getPageSize(), source.size());
    if (start > end) {
      return Page.empty(pageable);
    }
    List<Document> content = source.subList(start, end);
    return new PageImpl<>(content, pageable, source.size());
  }
}
