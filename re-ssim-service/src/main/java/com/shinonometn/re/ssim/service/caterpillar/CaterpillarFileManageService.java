package com.shinonometn.re.ssim.service.caterpillar;

import com.shinonometn.re.ssim.commons.file.fundation.FileServiceAdapter;
import org.springframework.stereotype.Service;

import java.io.File;

@Service
public class CaterpillarFileManageService extends FileServiceAdapter<String>{

    public CaterpillarFileManageService(File rootFolder) {
        super(rootFolder, "caterpillar");
    }
}
