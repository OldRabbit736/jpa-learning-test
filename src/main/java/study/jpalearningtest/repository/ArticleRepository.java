package study.jpalearningtest.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import study.jpalearningtest.entity.Article;

public interface ArticleRepository extends JpaRepository<Article, Long> {
}
