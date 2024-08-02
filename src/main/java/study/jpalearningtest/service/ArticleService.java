package study.jpalearningtest.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j;
import lombok.extern.log4j.Log4j2;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import study.jpalearningtest.entity.Article;
import study.jpalearningtest.repository.ArticleRepository;

import java.util.Optional;

@Log4j2
@RequiredArgsConstructor
@Service
public class ArticleService {

    private final ArticleRepository articleRepository;


    @Transactional
    public Long saveArticle(Article article) {
        return articleRepository.save(article).getId();
    }

    @Transactional
    public Optional<Article> getArticle(Long id) {
        Optional<Article> optionalArticle = articleRepository.findByIdWithOptimisticLock(id);
        if (optionalArticle.isPresent()) {
            Article article = optionalArticle.get();
            log.info(article.toString());
        }
        return optionalArticle;
    }

    /**
     * 위의 getArticle과 동일하지만 readOnly = true 옵션이 추가되었다.
     */
    @Transactional(readOnly = true)
    public Optional<Article> getArticleReadOnly(Long id) {
        Optional<Article> optionalArticle = articleRepository.findByIdWithOptimisticLock(id);
        if (optionalArticle.isPresent()) {
            Article article = optionalArticle.get();
            log.info(article.toString());
        }
        return optionalArticle;
    }
}
