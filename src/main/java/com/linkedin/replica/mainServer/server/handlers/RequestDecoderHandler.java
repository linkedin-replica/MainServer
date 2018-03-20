package com.linkedin.replica.mainServer.server.handlers;

import java.util.LinkedHashMap;

import com.linkedin.replica.mainServer.model.Request;

import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.handler.codec.http.HttpContent;
import io.netty.handler.codec.http.HttpRequest;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.netty.handler.codec.http.LastHttpContent;
import io.netty.util.CharsetUtil;

public class RequestDecoderHandler extends ChannelInboundHandlerAdapter{
	private StringBuilder builder;
	private Request request;
	
	public RequestDecoderHandler(){
		this.builder = new StringBuilder();
		this.request = new Request();
	}
	
	@Override
	public void channelRead(ChannelHandlerContext ctx, Object msg)	throws Exception {
		/*
		 * check if request is valid
		 */
		if(msg instanceof HttpRequest){
			HttpRequest httpRequest = (HttpRequest) msg;
			/*
			 *  split on start of query parameters to get an array of two strings where 
			 *  uriArr[0] = webSrevice uri
			 *  uriArr[1] = query parameters separated by & symbol
			 */
			int queryStringIdx = httpRequest.uri().indexOf('?');
			String queryParams = null;
			String requestURI;
			if(queryStringIdx == -1) {
				// no query string passed
				requestURI = httpRequest.uri();
			}else {
				requestURI = httpRequest.uri().substring(0, queryStringIdx);
				queryParams = httpRequest.uri().substring(queryStringIdx + 1);
			}
			
			/*
			 * uriContents should contains:
			 * requestURI = /api/search/companies?searchKey
			 *	uriContents[1] = api
			 *	uriContents[2] = {webServiceName} eg. notifications/search/wall
			 *	uriContents[3] = {functionalityName} eg. mark/set
			 */
			String[] uriContents = requestURI.split("/");
			// set attributes of request model
			request.setRequestURI(requestURI);
			request.setQueryParams(queryParams);
			// set prefURI to be api.{webServiceName} eg. api.notifications
			request.setWebServName(uriContents[1] + "." + uriContents[2]);
			
			if(uriContents.length == 4)
				request.setFuncName(uriContents[3]);
			
			request.setHeaders(httpRequest.headers());
			request.setMethod(httpRequest.method());
		}
			
		/*
		 * HttpContent holds the request body content. A request may have more than HttpContent block so
		 * builder will collect all HttpContents.
		 */
		if(msg instanceof HttpContent){
			HttpContent httpContent = (HttpContent) msg;
			builder.append(httpContent.content().toString(CharsetUtil.UTF_8));
			// release object to free memory
			((HttpContent) msg).release();
		}
		
		/*
		 * LastHttpContent has trailing headers which indicates the end of request.
		 */
		if(msg instanceof LastHttpContent){	
			// set body attribute of request to collected HttpContent (if exist)
			request.setBody(builder.toString());
			// release object to free memory
			((LastHttpContent) msg).release();
			// send decoded request to next handler (requestFilterationHandler)
			ctx.fireChannelRead(request);
		}
	}

	/**
	 * Overriding exceptionCaught()  to react to any Throwable.
	 */
	@Override
	public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
		// construct Error Response
		LinkedHashMap<String, Object> responseBody = new LinkedHashMap<String, Object>();
		
		// set Http status code
		responseBody.put("code", HttpResponseStatus.INTERNAL_SERVER_ERROR.code());
		responseBody.put("type", HttpResponseStatus.INTERNAL_SERVER_ERROR);
		responseBody.put("errMessage", cause.getMessage());
	
//		cause.printStackTrace();
		
		// send response to ResponseEncoderHandler
		ctx.writeAndFlush(responseBody);
	}
}
