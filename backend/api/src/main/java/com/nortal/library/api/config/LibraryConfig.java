package com.nortal.library.api.config;

import com.nortal.library.core.port.BookRepository;
import com.nortal.library.core.port.MemberRepository;
import com.nortal.library.core.service.LibraryService;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class LibraryConfig {

  @Bean
  LibraryService libraryService(BookRepository bookRepository, MemberRepository memberRepository) {
    return new LibraryService(bookRepository, memberRepository);
  }
}
