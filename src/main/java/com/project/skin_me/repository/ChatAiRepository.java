package com.project.skin_me.repository;

import com.project.skin_me.model.ChatAi;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ChatAiRepository extends JpaRepository<ChatAi, Long> {

    List<ChatAi> findAllByOrderByTimestampDesc();

    Page<ChatAi> findAllByOrderByTimestampDesc(Pageable pageable);

    @Query("SELECT c FROM ChatAi c WHERE c.session = :session ORDER BY c.timestamp DESC")
    List<ChatAi> findBySessionOrderByTimestampDesc(@Param("session") String session);

    Page<ChatAi> findBySessionOrderByTimestampDesc(@Param("session") String session, Pageable pageable);

    @Query("SELECT DISTINCT c.session FROM ChatAi c WHERE c.session IS NOT NULL AND c.session != '' ORDER BY c.session")
    List<String> findDistinctSessions();
}
