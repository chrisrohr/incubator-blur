package org.apache.blur.shell;

/**
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
import java.io.PrintWriter;
import java.io.Writer;

import org.apache.blur.thirdparty.thrift_0_9_0.TException;
import org.apache.blur.thrift.generated.Blur;
import org.apache.blur.thrift.generated.BlurException;
import org.apache.blur.thrift.generated.ColumnDefinition;
import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.CommandLineParser;
import org.apache.commons.cli.HelpFormatter;
import org.apache.commons.cli.Option;
import org.apache.commons.cli.OptionBuilder;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.ParseException;
import org.apache.commons.cli.PosixParser;

public class AddColumnDefinitionCommand extends Command {

  @Override
  public void doit(PrintWriter out, Blur.Iface client, String[] args) throws CommandException, TException,
      BlurException {
    if (args.length < 5) {
      throw new CommandException("Invalid args: " + help());
    }
    CommandLine cmd = parse(args, out);

    ColumnDefinition columnDefinition = new ColumnDefinition();
    columnDefinition.setFamily(args[2]);
    columnDefinition.setColumnName(args[3]);
    columnDefinition.setFieldType(args[4]);
    if (cmd.hasOption("s")) {
      columnDefinition.setColumnName(cmd.getOptionValue("s"));
    }
    if (cmd.hasOption("F")) {
      columnDefinition.setFieldLessIndexing(true);
    }
    if (cmd.hasOption("p")) {
      Option[] options = cmd.getOptions();
      for (Option option : options) {
        if (option.getOpt().equals("p")) {
          String[] values = option.getValues();
          columnDefinition.putToProperties(values[0], values[1]);
        }
      }
    }
    client.addColumnDefinition(args[1], columnDefinition);
  }

  @Override
  public String help() {
    return "define column, args: <table> <family> <column name> <type> [-s <sub column name>] [-F] [-p name value]*";
  }

  @SuppressWarnings("static-access")
  private static CommandLine parse(String[] otherArgs, Writer out) {
    Options options = new Options();
//    options.addOption(OptionBuilder.withArgName("family").hasArg().isRequired(true)
//        .withDescription("* The family of the new column definition.").create("f"));
//    options.addOption(OptionBuilder.withArgName("family").hasArg().isRequired(true)
//        .withDescription("* The family of the new column definition.").create("f"));
//    options.addOption(OptionBuilder.withArgName("column name").hasArg().isRequired(true)
//        .withDescription("* The column name of the new column definition.").create("c"));
    options.addOption(OptionBuilder.withArgName("sub column name").hasArg()
        .withDescription("The sub column name of the new column definition.").create("s"));
    options.addOption(OptionBuilder.withDescription(
        "Should the column definition be definied as a field less indexing column definition.").create("F"));
    options.addOption(OptionBuilder.withArgName("name value").hasArgs(2)
        .withDescription("Sets the properties for this column definition.").create("p"));

    CommandLineParser parser = new PosixParser();
    CommandLine cmd = null;
    try {
      cmd = parser.parse(options, otherArgs);
    } catch (ParseException e) {
      HelpFormatter formatter = new HelpFormatter();
      PrintWriter pw = new PrintWriter(out, true);
      formatter.printHelp(pw, HelpFormatter.DEFAULT_WIDTH, "definecolumn", null, options,
          HelpFormatter.DEFAULT_LEFT_PAD, HelpFormatter.DEFAULT_DESC_PAD, null, false);
      return null;
    }
    return cmd;
  }

}