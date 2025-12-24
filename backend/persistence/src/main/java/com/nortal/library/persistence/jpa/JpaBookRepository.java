package com.nortal.library.persistence.jpa;

import com.nortal.library.core.domain.Book;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface JpaBookRepository extends JpaRepository<Book, String> {
  @Query("SELECT COUNT(b) FROM Book b WHERE b.loanedTo = :memberId")
  int countByLoanedTo(@Param("memberId") String memberId);
}
