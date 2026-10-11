package cz.vsb.autarepo.autorepo.common.exception;

import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.servlet.ModelAndView;

@ControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(ResourceNotFoundException.class)
    public ModelAndView handleResourceNotFound(ResourceNotFoundException e) {
        ModelAndView mav = new ModelAndView("error/404");
        mav.addObject("errorMessage", e.getMessage());
        return mav;
    }

    @ExceptionHandler({ConflictOverlapException.class, CancellationWindowClosedException.class, ApprovalWindowExpiredException.class})
    public ModelAndView handleBusinessException(RuntimeException e) {
        ModelAndView mav = new ModelAndView("error/500");
        mav.addObject("errorMessage", e.getMessage());
        return mav;
    }

    @ExceptionHandler(Exception.class)
    public ModelAndView handleGenericException(Exception e) {
        ModelAndView mav = new ModelAndView("error/500");
        mav.addObject("errorMessage", "An unexpected error occurred: " + e.getMessage());
        return mav;
    }
}
