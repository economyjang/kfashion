package com.kfashion.kfashion.board;

import com.kfashion.kfashion.account.Account;
import com.kfashion.kfashion.account.AccountRepository;
import lombok.RequiredArgsConstructor;
import org.modelmapper.ModelMapper;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

@Service
@RequiredArgsConstructor
@Transactional
public class CommentService {

    private final ModelMapper mapper;
    private final BoardRepository boardRepository;
    private final AccountRepository accountRepository;
    private final CommentRepository commentRepository;

    public void processNewComment(Account account, CommentForm commentForm) {

        Comment comment;

        if (commentForm.getGroupId() == null) {
            comment = Comment.builder()
                    .comment(commentForm.getComment())
                    .nickName(account.getNickName())
                    .groupOrder(0L)
                    .intent(0L)
                    .commentTime(LocalDateTime.now().format(DateTimeFormatter.ofPattern("YYYY.MM.dd. HH:mm")))
                    .build();
        } else {

            Long interruptPosition = commentRepository.interruptPosition(commentForm.getGroupId(),
                    commentForm.getGroupOrder(), commentForm.getIntent());

            if(interruptPosition == null){
                comment = Comment.builder()
                        .comment(commentForm.getComment())
                        .nickName(account.getNickName())
                        .parentNickName(commentRepository.findById(commentForm.getCommentId()).get().getNickName())
                        .groupId(commentForm.getGroupId())
                        .groupOrder(commentRepository.maxGroupOrder(commentForm.getGroupId()) + 1)
                        .intent(commentForm.getIntent() + 1)
                        .commentTime(LocalDateTime.now().format(DateTimeFormatter.ofPattern("YYYY.MM.dd HH:mm")))
                        .build();
            }   else {

                List<Comment> commentList = commentRepository.findByGroupId(commentForm.getGroupId());

                for(Comment comments : commentList){
                    if(comments.getGroupOrder() >= interruptPosition){
                        comments.moveGroupOrder();
                    }
                }
                commentRepository.saveAll(commentList);

                comment = Comment.builder()
                        .comment(commentForm.getComment())
                        .nickName(account.getNickName())
                        .parentNickName(commentRepository.findById(commentForm.getCommentId()).get().getNickName())
                        .groupId(commentForm.getGroupId())
                        .groupOrder(interruptPosition)
                        .intent(commentForm.getIntent() + 1)
                        .commentTime(LocalDateTime.now().format(DateTimeFormatter.ofPattern("YYYY.MM.dd HH:mm")))
                        .build();
            }

        }

        Comment savedComment = commentRepository.save(comment);
        accountRepository.save(account).addComment(savedComment);
        Optional<Board> board = boardRepository.findById(commentForm.getBoardId());
        board.get().addComment(comment);

        if(comment.getGroupId()==null){
            setCommentGroupId(savedComment);
        }
    }


    private void setCommentGroupId(Comment comment) {
        comment.setGroupId(comment.getId());
    }

    public Map<String,Object> getAllCommentList(CommentForm commentForm, Pageable pageable) {
        Page<Comment> allByBoardOwnerId = commentRepository.findAllByBoardOwnerId(commentForm.getBoardId(), pageable);
        List<CommentForm> commentFormList = mappingEntityToDto(allByBoardOwnerId);

        Map<String,Object> result = new HashMap<>();
        result.put("commentFormList", commentFormList);
        result.put("pageNumber", allByBoardOwnerId.getNumber());
        result.put("pageTotalPages", allByBoardOwnerId.getTotalPages());

        return result;
    }

    private List<CommentForm> mappingEntityToDto(Page<Comment> commentList) {
        List<CommentForm> commentFormList = new ArrayList<>();
        for (Comment comment : commentList) {
            CommentForm commentForm = mapper.map(comment, CommentForm.class);
            commentForm.setAccountId(comment.getAccountOwner().getId());
            commentFormList.add(commentForm);
        }
        return commentFormList;
    }


    public void deleteComment(Account account, CommentForm commentForm) {
        Optional<Comment> comment = commentRepository.findById(commentForm.getCommentId());
        Optional<Board> board = boardRepository.findById(commentForm.getBoardId());

        accountRepository.save(account).removeComment(comment);
        board.get().removeComment(comment);
        commentRepository.deleteById(commentForm.getCommentId());
    }

    public void updateComment(CommentForm commentForm) {
        commentRepository.findById(
                commentForm.getCommentId()).get().setComment(commentForm.getComment());
    }
}