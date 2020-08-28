/*
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.prestosql.plugin.oracle;

import com.google.common.collect.ImmutableMap;
import org.testng.annotations.Test;

import java.math.RoundingMode;
import java.util.Map;

import static io.airlift.configuration.testing.ConfigAssertions.assertFullMapping;
import static io.airlift.configuration.testing.ConfigAssertions.assertRecordedDefaults;
import static io.airlift.configuration.testing.ConfigAssertions.recordDefaults;

public class TestOracleConfig
{
    @Test
    public void testDefaults()
    {
        assertRecordedDefaults(recordDefaults(OracleConfig.class)
                .setSynonymsEnabled(false)
                .setVarcharMaxSize(4000)
                .setNumberDefaultScale(10)
                .setNumberRoundingMode(RoundingMode.HALF_UP));
    }

    @Test
    public void testExplicitPropertyMappings()
    {
        Map<String, String> properties = new ImmutableMap.Builder<String, String>()
                .put("oracle.synonyms.enabled", "true")
                .put("oracle.varchar.max-size", "10000")
                .put("oracle.number.default-scale", "2")
                .put("oracle.number.rounding-mode", "CEILING")
                .build();

        OracleConfig expected = new OracleConfig()
                .setSynonymsEnabled(true)
                .setVarcharMaxSize(10000)
                .setNumberDefaultScale(2)
                .setNumberRoundingMode(RoundingMode.CEILING);

        assertFullMapping(properties, expected);
    }
}
