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
package io.prestosql.operator;

import io.prestosql.execution.Lifespan;
import io.prestosql.spi.Page;
import io.prestosql.sql.planner.plan.PlanNodeId;

public interface WorkProcessorOperatorFactory
{
    int getOperatorId();

    PlanNodeId getPlanNodeId();

    String getOperatorType();

    WorkProcessorOperator create(
            ProcessorContext processorContext,
            WorkProcessor<Page> sourcePages);

    default void lifespanFinished(Lifespan lifespan)
    {
        //do nothing
    }

    default void close()
    {
        //do nothing
    }
}
