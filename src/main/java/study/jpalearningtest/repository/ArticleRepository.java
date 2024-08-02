package study.jpalearningtest.repository;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import study.jpalearningtest.entity.Article;

import java.util.Optional;

public interface ArticleRepository extends JpaRepository<Article, Long> {

    @Lock(LockModeType.OPTIMISTIC)
    @Query("select a from Article a where a.id = :id")
    Optional<Article> findByIdWithOptimisticLock(@Param("id") Long id);
}
