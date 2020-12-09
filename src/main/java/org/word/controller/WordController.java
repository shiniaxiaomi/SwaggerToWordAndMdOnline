package org.word.controller;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.serializer.SerializerFeature;
import io.swagger.annotations.*;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.thymeleaf.context.Context;
import org.thymeleaf.spring5.SpringTemplateEngine;
import org.word.model.ModelAttr;
import org.word.model.Request;
import org.word.model.Table;
import org.word.service.WordService;

import javax.servlet.http.HttpServletResponse;
import javax.validation.Valid;
import java.io.BufferedOutputStream;
import java.io.IOException;
import java.net.URLEncoder;
import java.util.*;


/**
 * Created by XiuYin.Cui on 2018/1/11.
 */
@Controller
@Api(tags = "the toWord API")
public class WordController {

    @Autowired
    private WordService tableService;
    @Autowired
    private SpringTemplateEngine springTemplateEngine;

    /**
     * 将 swagger 文档转换成 html 文档，可通过在网页上右键另存为 xxx.doc 的方式转换为 word 文档
     *
     * @param model
     * @param url   需要转换成 word 文档的资源地址
     * @return
     */
    @Deprecated
    @ApiOperation(value = "将 swagger 文档转换成 html 文档，可通过在网页上右键另存为 xxx.doc 的方式转换为 word 文档", response = String.class, tags = {"Word"})
    @ApiResponses(value = {@ApiResponse(code = 200, message = "请求成功。", response = String.class)})
    @RequestMapping(value = "/toWord", method = {RequestMethod.GET})
    public String getWord(Model model,
                          @ApiParam(value = "资源地址", required = false) @RequestParam(value = "url", required = false) String url,
                          @ApiParam(value = "是否下载", required = false) @RequestParam(value = "download", required = false, defaultValue = "1") Integer download) {
        generateModelData(model, url, download);
        return "word";
    }

    @ApiOperation(value = "将 swagger 文档转换成 markdown 文档", response = String.class, tags = {"Markdown"})
    @RequestMapping(value = "/toMarkdown", method = {RequestMethod.GET})
    public void toMarkdown(Model model,HttpServletResponse response,
                          @ApiParam(value = "资源地址", required = false) @RequestParam(value = "url", required = false) String url) {
        generateModelData(model, url, 1);
        Map<String, Object> map = model.asMap();
        Map controllerMap = (Map) map.get("tableMap");
        StringBuilder sb = new StringBuilder();
        Iterator iterator = controllerMap.keySet().iterator();
        while(iterator.hasNext()){
            ArrayList<Table> method = (ArrayList<Table>) controllerMap.get(iterator.next());

            for(Table table:method){
                sb.append("**简要描述：**\n\n");
                sb.append(String.format("- %s\n\n",table.getDescription()));
                sb.append("**请求URL：**\n\n");
                sb.append(String.format("- ` %s `\n\n",table.getUrl()));
                sb.append("**请求方式：**\n\n");
                sb.append(String.format("- %s\n\n",table.getRequestType()));
                sb.append("**请求参数：**\n\n");
                List<Request> requestList = table.getRequestList();
                sb.append(String.format("%s\n\n",buildRequestTable(requestList)));
                sb.append("**请求示例**\n\n");
                sb.append(buildExample(table.getRequestParam()));
                sb.append("**返回示例**\n\n");
                sb.append(buildExample(table.getResponseParam()));
                sb.append("**返回参数说明**\n\n");
                List<ModelAttr> properties = table.getModelAttr().getProperties();
                sb.append(String.format("%s\n\n",buildResponseTable(properties)));
                sb.append("**备注**\n\n");
                sb.append("- 更多返回错误代码请看首页的错误代码描述\n\n");
                sb.append("---\n\n");
            }

        }

        sendMarkdownFile(sb,response);
    }

    private String buildExample(String s) {
        Object param = null;

        if(s==null || "".equals(s)){
            return "- 无\n\n";
        }

        //Post
        if(s.startsWith(" -d ")){
            int start = s.indexOf("{");
            int end = s.lastIndexOf("}")+1;
            s = s.substring(start, end);
            try{
                param = JSON.parse(s);
            }catch (Exception e){
                System.out.println(e);
            }

        }
        //JSON
        else if(s.startsWith("{") && s.endsWith("}")){
            param = JSON.parse(s);
        }
        //get
        else{
            String[] split = s.split("&");
            HashMap<Object, Object> map = new HashMap<>();
            for(String str:split){
                String[] split1 = str.split("=");
                if(split1.length==1){
                    map.put(split1[0],"");
                    continue;
                }
                map.put(split1[0],split1[1]);
            }
            param=map;
        }


        return String.format("```java\n%s\n```\n\n",JSON.toJSONString(param,SerializerFeature.PrettyFormat) );
    }


    //构建参数列表
    public String buildResponseTable(List<ModelAttr> parameters){
        if(parameters.isEmpty()){
            return "- 无\n\n";
        }

        StringBuilder sb = new StringBuilder();
        sb.append("|参数名|类型|说明|\n");
        sb.append("|----|----|-----|-----|\n");
        parameters.forEach(parameter -> {
            //参数名，类型，说明
            sb.append(String.format("|%s|%s|%s|\n",
                    parameter.getName(),
                    parameter.getType(),
                    parameter.getDescription()==null?"null":parameter.getDescription())
            );
        });
        sb.append("\n");
        return sb.toString();
    }

    //构建参数列表
    public String buildRequestTable(List<Request> parameters){
        if(parameters.isEmpty()){
            return "- 无\n\n";
        }

        StringBuilder sb = new StringBuilder();
        sb.append("|参数名|必选|类型|说明|\n");
        sb.append("|----|----|-----|-----|\n");
        parameters.forEach(parameter -> {
            //参数名，必选，类型，说明
            sb.append(String.format("|%s|%s|%s|%s|\n",
                    parameter.getName(),
                    parameter.getRequire(),
                    parameter.getParamType(),
                    parameter.getRemark()==null?"null":parameter.getRemark())
            );
        });
        sb.append("\n");
        return sb.toString();
    }


    private void generateModelData(Model model, String url, Integer download) {
        url = StringUtils.defaultIfBlank(url, "https://petstore.swagger.io/v2/swagger.json");
        Map<String, Object> result = tableService.tableList(url);
        model.addAttribute("url", url);
        model.addAttribute("download", download);
        model.addAllAttributes(result);
    }

    /**
     * 将 swagger 文档一键下载为 doc 文档
     *
     * @param model
     * @param url      需要转换成 word 文档的资源地址
     * @param response
     */
    @ApiOperation(value = "将 swagger 文档一键下载为 doc 文档", notes = "", tags = {"Word"})
    @ApiResponses(value = {@ApiResponse(code = 200, message = "请求成功。")})
    @RequestMapping(value = "/downloadWord", method = {RequestMethod.GET})
    public void word(Model model, @ApiParam(value = "资源地址", required = false) @RequestParam(required = false) String url, HttpServletResponse response) {
        generateModelData(model, url, 0);
        writeContentToResponse(model, response);
    }

    private void writeContentToResponse(Model model, HttpServletResponse response) {
        Context context = new Context();
        context.setVariables(model.asMap());
        String content = springTemplateEngine.process("word", context);
        response.setContentType("application/octet-stream;charset=utf-8");
        response.setCharacterEncoding("utf-8");
        try (BufferedOutputStream bos = new BufferedOutputStream(response.getOutputStream())) {
            response.setHeader("Content-disposition", "attachment;filename=" + URLEncoder.encode("toWord.doc", "utf-8"));
            byte[] bytes = content.getBytes();
            bos.write(bytes, 0, bytes.length);
            bos.flush();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void sendMarkdownFile(StringBuilder sb,HttpServletResponse response) {
        response.setContentType("application/octet-stream;charset=utf-8");
        response.setCharacterEncoding("utf-8");
        try (BufferedOutputStream bos = new BufferedOutputStream(response.getOutputStream())) {
            response.setHeader("Content-disposition", "attachment;filename=" + URLEncoder.encode("toMarkdown.md", "utf-8"));
            byte[] bytes = sb.toString().getBytes();
            bos.write(bytes, 0, bytes.length);
            bos.flush();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    /**
     * 将 swagger json文件转换成 word文档并下载
     *
     * @param model
     * @param jsonFile 需要转换成 word 文档的swagger json文件
     * @param response
     * @return
     */
    @ApiOperation(value = "将 swagger json文件转换成 word文档并下载", notes = "", tags = {"Word"})
    @ApiResponses(value = {@ApiResponse(code = 200, message = "请求成功。")})
    @RequestMapping(value = "/fileToWord", method = {RequestMethod.POST})
    public void getWord(Model model, @ApiParam("swagger json file") @Valid @RequestPart("jsonFile") MultipartFile jsonFile, HttpServletResponse response) {
        generateModelData(model, jsonFile);
        writeContentToResponse(model, response);
    }

    /**
     * 将 swagger json字符串转换成 word文档并下载
     *
     * @param model
     * @param jsonStr  需要转换成 word 文档的swagger json字符串
     * @param response
     * @return
     */
    @ApiOperation(value = "将 swagger json字符串转换成 word文档并下载", notes = "", tags = {"Word"})
    @ApiResponses(value = {@ApiResponse(code = 200, message = "请求成功。")})
    @RequestMapping(value = "/strToWord", method = {RequestMethod.POST})
    public void getWord(Model model, @ApiParam("swagger json string") @Valid @RequestParam("jsonStr") String jsonStr, HttpServletResponse response) {
        generateModelData(model, jsonStr);
        writeContentToResponse(model, response);
    }

    private void generateModelData(Model model, String jsonStr) {
        Map<String, Object> result = tableService.tableListFromString(jsonStr);
        model.addAttribute("url", "http://");
        model.addAttribute("download", 0);
        model.addAllAttributes(result);
    }

    private void generateModelData(Model model, MultipartFile jsonFile) {
        Map<String, Object> result = tableService.tableList(jsonFile);
        model.addAttribute("url", "http://");
        model.addAttribute("download", 0);
        model.addAllAttributes(result);
    }
}
