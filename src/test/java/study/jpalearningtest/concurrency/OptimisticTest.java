package study.jpalearningtest.concurrency;

import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import jakarta.persistence.LockModeType;
import jakarta.persistence.OptimisticLockException;
import jakarta.persistence.RollbackException;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.Commit;
import org.springframework.transaction.annotation.Transactional;
import study.jpalearningtest.entity.Article;
import study.jpalearningtest.repository.ArticleRepository;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import static org.assertj.core.api.Assertions.*;

@Transactional
@Slf4j
@SpringBootTest
public class OptimisticTest {

    @Autowired
    ArticleRepository articleRepository;
    @Autowired
    EntityManager em;
    @Autowired
    EntityManagerFactory emf;

    @BeforeEach
    void setup() {
    }

    @DisplayName("Integer version 시작 값은 0이다.")
    @Test
    void versionStartsFromZero() {
        Article article = new Article("타이틀");
        em.persist(article);
        em.flush();
        em.clear();

        Article foundArticle = em.find(Article.class, article.getId());
        assertThat(foundArticle.getVersion()).isEqualTo(0);
    }

    @DisplayName("LockModeType.None 모드에서는 버저닝 된 엔티티 수정 시에 버전이 올라간다.")
    @Test
    void noneMode_1() {
        Article article = new Article("타이틀");
        em.persist(article);
        em.flush();
        em.clear();

        Article foundArticle = em.find(Article.class, article.getId());
        foundArticle.setTitle("타이틀2");
        em.flush();
        em.clear();

        Article foundArticle2 = em.find(Article.class, foundArticle.getId());
        assertThat(foundArticle2.getVersion()).isEqualTo(1);
    }

    @Commit
    @DisplayName("LockModeType.OPTIMISTIC 모드에서는 조회한 엔티티도 트랜잭션 커밋 시 버전 체크한다.")
    @Test
    void optimisticMode() {
        Article article = new Article("타이틀");
        em.persist(article);
        em.flush();
        em.clear();

        Article foundArticle = em.find(Article.class, article.getId(),
                LockModeType.OPTIMISTIC);
    }

    @DisplayName("LockModeType.OPTIMISTIC 모드에서는 조회한 엔티티도 트랜잭션 커밋 시 버전 체크하여 버전이 변경되면 예외를 던진다.")
    @Test
    void optimisticMode2() {
        // given1 - 테스트 데이터 Article 저장
        EntityManager em1 = emf.createEntityManager();
        em1.getTransaction().begin();

        Article article = new Article("타이틀");
        em1.persist(article);
        em1.getTransaction().commit();

        // given2 - Article을 OPTIMISTIC 모드로 조회: 조회만 해도 커밋 시점에 version 체크 수행
        em1.getTransaction().begin();
        log.info("트랜잭션1 시작");
        Article foundArticle = em1.find(Article.class, article.getId(),
                LockModeType.OPTIMISTIC);

        assertThat(foundArticle.getVersion()).isEqualTo(0);

        // when - 트랜잭션2에서 Article 수정 (버전 변경)
        try (ExecutorService es = Executors.newFixedThreadPool(1)) {
            es.submit(() -> {
                EntityManager em2 = emf.createEntityManager();
                em2.getTransaction().begin();
                log.info("트랜잭션2 시작");

                Article foundArticle2 = em2.find(Article.class, article.getId());
                assertThat(foundArticle2.getVersion()).isEqualTo(0); // 조회 시 verion 0

                foundArticle2.setTitle("타이틀2");
                // 커밋 시 자동 버전 변경 version 0 -> 1
                // update article set title='타이틀2',version=1 where id=1 and version=0;
                em2.getTransaction().commit();
                log.info("트랜잭션2 종료");
            });
        }

        // then - 트랜잭션1에서 조회한 엔티티를 커밋 시점에 버전 체크 하는데, 트랜잭션2가 버전을 변경하였으므로 예외 발생
        // select version as version_ from article where id=1;
        assertThatThrownBy(() -> em1.getTransaction().commit())
                .isInstanceOf(RollbackException.class)
                .hasCauseExactlyInstanceOf(OptimisticLockException.class);
    }
}
