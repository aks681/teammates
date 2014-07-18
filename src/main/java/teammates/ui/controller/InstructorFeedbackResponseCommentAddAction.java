package teammates.ui.controller;

import java.util.ArrayList;
import java.util.Date;

import teammates.common.datatransfer.CommentSendingState;
import teammates.common.datatransfer.FeedbackParticipantType;
import teammates.common.datatransfer.FeedbackResponseAttributes;
import teammates.common.datatransfer.FeedbackResponseCommentAttributes;
import teammates.common.datatransfer.FeedbackSessionAttributes;
import teammates.common.datatransfer.InstructorAttributes;
import teammates.common.exception.EntityDoesNotExistException;
import teammates.common.exception.InvalidParametersException;
import teammates.common.util.Assumption;
import teammates.common.util.Const;
import teammates.logic.api.GateKeeper;

import com.google.appengine.api.datastore.Text;

public class InstructorFeedbackResponseCommentAddAction extends Action {

    @Override
    protected ActionResult execute() throws EntityDoesNotExistException {
        String courseId = getRequestParamValue(Const.ParamsNames.COURSE_ID);
        Assumption.assertNotNull("null course id", courseId);
        String feedbackSessionName = getRequestParamValue(Const.ParamsNames.FEEDBACK_SESSION_NAME);
        Assumption.assertNotNull("null feedback session name", feedbackSessionName);
        String feedbackQuestionId = getRequestParamValue(Const.ParamsNames.FEEDBACK_QUESTION_ID);
        Assumption.assertNotNull("null feedback question id", feedbackQuestionId);
        String feedbackResponseId = getRequestParamValue(Const.ParamsNames.FEEDBACK_RESPONSE_ID);
        Assumption.assertNotNull("null feedback response id", feedbackResponseId);
        
        InstructorAttributes instructor = logic.getInstructorForGoogleId(courseId, account.googleId);
        FeedbackSessionAttributes session = logic.getFeedbackSession(feedbackSessionName, courseId);
        FeedbackResponseAttributes response = logic.getFeedbackResponse(feedbackResponseId);
        Assumption.assertNotNull(response);
        boolean isCreatorOnly = true;
        
        new GateKeeper().verifyAccessible(instructor, session, !isCreatorOnly, 
                response.giverSection, feedbackSessionName,
                Const.ParamsNames.INSTRUCTOR_PERMISSION_SUBMIT_SESSION_IN_SECTIONS);
        new GateKeeper().verifyAccessible(instructor, session, !isCreatorOnly, 
                response.recipientSection, feedbackSessionName,
                Const.ParamsNames.INSTRUCTOR_PERMISSION_SUBMIT_SESSION_IN_SECTIONS);
        
        InstructorFeedbackResponseCommentAjaxPageData data = 
                new InstructorFeedbackResponseCommentAjaxPageData(account);
        
        String commentText = getRequestParamValue(Const.ParamsNames.FEEDBACK_RESPONSE_COMMENT_TEXT);
        Assumption.assertNotNull("null comment text", commentText);
        if (commentText.trim().isEmpty()) {
            data.errorMessage = Const.StatusMessages.FEEDBACK_RESPONSE_COMMENT_EMPTY;
            data.isError = true;
            return createAjaxResult(Const.ViewURIs.INSTRUCTOR_FEEDBACK_RESULTS_BY_RECIPIENT_GIVER_QUESTION, data);
        }
        
        FeedbackResponseCommentAttributes feedbackResponseComment = new FeedbackResponseCommentAttributes(courseId,
            feedbackSessionName, feedbackQuestionId, instructor.email, feedbackResponseId, new Date(),
            new Text(commentText), response.giverSection, response.recipientSection);
        
        String showCommentTo = getRequestParamValue(Const.ParamsNames.RESPONSE_COMMENTS_SHOWCOMMENTSTO);
        String showGiverNameTo = getRequestParamValue(Const.ParamsNames.RESPONSE_COMMENTS_SHOWGIVERTO);
        feedbackResponseComment.showCommentTo = new ArrayList<FeedbackParticipantType>();
        if(showCommentTo != null && !showCommentTo.isEmpty()){
            String[] showCommentToArray = showCommentTo.split(",");
            for(String viewer:showCommentToArray){
                feedbackResponseComment.showCommentTo.add(FeedbackParticipantType.valueOf(viewer.trim()));
            }
        }
        feedbackResponseComment.showGiverNameTo = new ArrayList<FeedbackParticipantType>();
        if(showGiverNameTo != null && !showGiverNameTo.isEmpty()){
            String[] showGiverNameToArray = showGiverNameTo.split(",");
            for(String viewer:showGiverNameToArray){
                feedbackResponseComment.showGiverNameTo.add(FeedbackParticipantType.valueOf(viewer.trim()));
            }
        }
        
        if(isResponseCommentPublicToRecipient(feedbackResponseComment)){
            feedbackResponseComment.sendingState = CommentSendingState.PENDING;
        }
        
        FeedbackResponseCommentAttributes createdComment = new FeedbackResponseCommentAttributes();
        try {
            createdComment = logic.createFeedbackResponseComment(feedbackResponseComment);
            logic.putDocument(createdComment);
        } catch (InvalidParametersException e) {
            setStatusForException(e);
            data.errorMessage = e.getMessage();
            data.isError = true;
        }
        
        if (!data.isError) {
            statusToAdmin += "InstructorFeedbackResponseCommentAddAction:<br>"
                    + "Adding comment to response: " + feedbackResponseComment.feedbackResponseId + "<br>"
                    + "in course/feedback session: " + feedbackResponseComment.courseId + "/" 
                    + feedbackResponseComment.feedbackSessionName + "<br>"
                    + "by: " + feedbackResponseComment.giverEmail + " at " + feedbackResponseComment.createdAt + "<br>"
                    + "comment text: " + feedbackResponseComment.commentText.getValue();
        }
        
        data.comment = createdComment;
        
        return createAjaxResult(Const.ViewURIs.INSTRUCTOR_FEEDBACK_RESULTS_BY_RECIPIENT_GIVER_QUESTION, data);
    }

    private boolean isResponseCommentPublicToRecipient(FeedbackResponseCommentAttributes comment) {
        return (comment.isVisibleTo(FeedbackParticipantType.GIVER)
                    || comment.isVisibleTo(FeedbackParticipantType.RECEIVER)
                    || comment.isVisibleTo(FeedbackParticipantType.OWN_TEAM_MEMBERS)
                    || comment.isVisibleTo(FeedbackParticipantType.RECEIVER_TEAM_MEMBERS)
                    || comment.isVisibleTo(FeedbackParticipantType.STUDENTS));
    }
}
