# The picture behind the mouth diagrams

`2303_Anatomy_of_Nose-Pharynx-Mouth-Larynx.jpg` is kept here exactly as published.

- Title: Anatomy of Nose-Pharynx-Mouth-Larynx (figure 2303)
- Author: OpenStax College
- From: *Anatomy & Physiology*, Connexions Web site, http://cnx.org/content/col11496/1.6/, 19 June 2013
- Copy used: https://commons.wikimedia.org/wiki/File:2303_Anatomy_of_Nose-Pharynx-Mouth-Larynx.jpg
- Licence: Creative Commons Attribution 3.0 Unported (CC BY 3.0), https://creativecommons.org/licenses/by/3.0/

## What the app changes

`tools/mouth/build_mouth.py` makes the pictures in `app/src/main/assets/mouth/` from this file:

- the printed labels and their pointer lines are removed and the picture is cropped to the head;
- the two tonsils are painted out, and everything that is not mouth or skin is washed out, so that
  lips, teeth and tongue stand out;
- the tongue, the lower jaw (with lower lip and teeth) and the soft palate are cut out as separate
  pieces, and the area behind them is painted in;
- in the app those pieces are moved and bent to show the position of the mouth for each Mandarin
  sound, and markers for contact, breath and voice are drawn on top.

The adapted pictures are shared under the same licence.
