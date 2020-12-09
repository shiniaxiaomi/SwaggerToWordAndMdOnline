package org.word.controller;


import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.ApiResponse;
import io.swagger.annotations.ApiResponses;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.servlet.ModelAndView;
import springfox.documentation.annotations.ApiIgnore;

import javax.servlet.http.HttpServletRequest;

/**
 * @author xiuyin.cui
 * @Description
 * @date 2019/3/22 10:52
 */
@Controller
public class IndexController {

    @ApiIgnore
    @RequestMapping(value = "/")
    public String index() {
        return "index";
    }

    @ApiIgnore
    @RequestMapping(value = "/urlCommit")
    public ModelAndView urlCommit(String url) {
        ModelAndView modelAndView = new ModelAndView();
        modelAndView.setViewName("forward:/toWord");
        return modelAndView;
    }
}
