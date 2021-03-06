package com.shinonometn.re.ssim.application.configuration.preparation.endpoint.scanning;

import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.mvc.method.RequestMappingInfo;

public interface ApiMethodSignatureExtractor {
    EndpointInformation.SignatureInfo extract(RequestMappingInfo requestMappingInfo, HandlerMethod handlerMethod);
}
