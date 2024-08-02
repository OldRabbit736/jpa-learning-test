package study.jpalearningtest.concurrency;

import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import jakarta.transaction.Transactional;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import study.jpalearningtest.entity.Article;

@SpringBootTest
public class PessimisticLockingTest {

    @Autowired
    EntityManager em;

    @Transactional
    @Test
    void pessimisticRead() {
        Article article = new Article("타이틀");
        em.persist(article);
        em.flush();
        em.clear();

        // select a1_0.id,a1_0.title,a1_0.version from article a1_0 where a1_0.id=1 for share;
        Article foundArticle = em.find(Article.class, article.getId(), LockModeType.PESSIMISTIC_READ);
    }
}
