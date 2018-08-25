package com.aurea.testgenerator.generation.names;

import com.aurea.testgenerator.source.Unit;

public class NullCheckingTestClassNomenclature implements TestClassNomenclature {

    @Override
    public String requestTestClassName(Unit unit) {
        return unit.getClassName() + "NullCheckingTest";
    }
}
