package com.nortal.library.core.test.service;

import com.nortal.library.core.port.BookRepository;
import com.nortal.library.core.port.MemberRepository;

import com.nortal.library.core.sevice.LibraryService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;


@ExtendWith(MockitoExtension.class)
public class LibraryServiceTest {

    @Mock
    private BookRepository bookRepository;

    @Mock
    private MemberRepository memberRepository;

    @InjectMocks
    private LibraryService libraryService;

    @Test
    void shouldFailWhenBookNotFound(){

    }

}
