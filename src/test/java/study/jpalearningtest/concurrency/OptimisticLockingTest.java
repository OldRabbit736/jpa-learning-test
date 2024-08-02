package study.jpalearningtest.concurrency;

import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import jakarta.persistence.LockModeType;
import jakarta.persistence.OptimisticLockException;
import jakarta.persistence.RollbackException;
import lombok.extern.log4j.Log4j2;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.Commit;
import org.springframework.transaction.annotation.Transactional;
import study.jpalearningtest.entity.Article;
import study.jpalearningtest.repository.ArticleRepository;
import study.jpalearningtest.service.ArticleService;

import java.util.Optional;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import static org.assertj.core.api.Assertions.*;

@Log4j2
@SpringBootTest
public class OptimisticLockingTest {

    @Autowired
    ArticleRepository articleRepository;
    @Autowired
    EntityManager em;
    @Autowired
    EntityManagerFactory emf;
    @Autowired
    ArticleService articleService;

    @BeforeEach
    void setup() {
        articleRepository.deleteAll();
        log.info("setup complete");
    }

    @Transactional
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

    @Transactional
    @DisplayName("LockModeType.None 모드에서는 버저닝 된 엔티티 수정 시에 버전이 올라간다.")
    @Test
    void noneMode_1() {
        Article article = new Article("타이틀");
        em.persist(article);
        em.flush();
        em.clear();

        Article foundArticle = em.find(Article.class, article.getId());
        foundArticle.setTitle("타이틀2");
        em.flush(); // update article set title='타이틀2',version=1 where id=1 and version=0;
        em.clear();

        Article foundArticle2 = em.find(Article.class, foundArticle.getId());
        assertThat(foundArticle2.getVersion()).isEqualTo(1);
    }

    @Transactional
    @Commit
    @DisplayName("LockModeType.OPTIMISTIC 모드에서는 조회한 엔티티도 트랜잭션 커밋 시 버전 체크한다.")
    @Test
    void optimisticMode_thenCheckVersionOnRead() {
        Article article = new Article("타이틀");
        em.persist(article);
        em.flush();
        em.clear();

        Article foundArticle = em.find(Article.class, article.getId(),
                LockModeType.OPTIMISTIC);
        // 커밋 시 select version as version_ from article where id=1; 발생!
    }

    @DisplayName("LockModeType.OPTIMISTIC 모드에서는 조회한 엔티티도 트랜잭션 커밋 시 버전 체크하여 버전이 변경되면 예외를 던진다.")
    @Test
    void optimisticMode_concurrentDetected() {
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

    // 이번에는 EntityManager를 직접 사용하는 대신, Spring Data JPA 리포지토리와 Transactional 걸린 서비스를 이용해
    // LockModeType.OPTIMISTIC 을 테스트한다.
    @DisplayName("LockModeType.OPTIMISTIC 모드에서는 조회한 엔티티도 트랜잭션 커밋 시 버전 체크한다.")
    @Test
    void optimisticMode_transactionalService() {
        // given - article saved
        Long id = articleService.saveArticle(new Article("타이틀"));

        // when - Sprint Data JPA 리포지토리와 트랜잭셔널 서비스 메소드 사용
        Optional<Article> article = articleService.getArticle(id);
        // 커밋 시 select version as version_ from article where id=1; 발생!
    }

    @DisplayName("read only 트랜잭셔널 걸린 상태에서도 LockModeType.OPTIMISTIC 모드에서는 조회한 엔티티도 트랜잭션 커밋 시 버전 체크한다.")
    @Test
    void optimisticMode_readOnlyTransactionalService() {
        // given - article saved
        Long id = articleService.saveArticle(new Article("타이틀"));

        // when - Sprint Data JPA 리포지토리와 readonly 트랜잭셔널 서비스 메소드 사용
        Optional<Article> article = articleService.getArticleReadOnly(id);
        // 커밋 시 select version as version_ from article where id=1; 발생!
    }

}
